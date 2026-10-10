package com.flowerconnect.customer;

import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.dto.AddressRequest;
import com.flowerconnect.customer.dto.AddressResponse;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.customer.service.AddressService;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency coverage for the address-book default rule (plan task 4.1).
 *
 * <p>The one-default-per-customer invariant is a <em>service-level</em>
 * rule: MySQL has no partial unique index, and the generated-column trick
 * cannot coexist with the required {@code user_id} foreign key (MySQL error
 * 1215 — the D-21 finding). It is enforced by {@link AddressService} under
 * a pessimistic lock on the owning {@code users} row, and a lock cannot be
 * verified by a test that never runs two transactions at once — run one
 * after another, every call sees the previous one's committed rows, so an
 * unlocked implementation would pass.
 *
 * <p>These tests exist because of the case an address-row lock cannot
 * cover: a customer creating their <em>first</em> address has no address
 * row to lock. Two simultaneous first-address creations would otherwise
 * both observe "no addresses exist" and both set the default flag. Locking
 * the user row — which always exists — serialises every default-mutating
 * transaction for one customer in a fixed order.
 *
 * <p>The service is called directly rather than through HTTP so each thread
 * gets a genuinely separate transaction with no shared MockMvc or security
 * context in the way; the transactions are the same {@code @Transactional}
 * methods the controller invokes. {@code @SpringBootTest} without
 * {@code @Transactional} is essential here — a test-managed transaction
 * would hold the changes uncommitted and hide exactly the interleaving
 * being tested. Every assertion is about the <em>end state</em>: how many
 * addresses exist, and how many of them hold the default, after the threads
 * are released together by a latch.
 */
@SpringBootTest
class AddressConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AddressService addressService;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;

    private User customer;
    private Long serviceLocationId;

    @BeforeEach
    void setUp() {
        customer = createCustomer();
        serviceLocationId = serviceLocationRepository
                .findAll(Sort.by("id")).get(0).getId();
    }

    // ------------------------------------------------------------------
    // Simultaneous first-address creation
    // ------------------------------------------------------------------

    @Test
    void simultaneousFirstAddressCreationsLeaveExactlyOneDefault() throws Exception {
        int threads = 8;

        List<Throwable> failures = runConcurrently(threads, index ->
                addressService.create(customer.getEmail(), AddressRequest.builder()
                        .label("Home " + index)
                        .line1(index + " Example Street")
                        .serviceLocationId(serviceLocationId)
                        .build()));

        assertNoFailures(failures);
        List<Address> addresses = addressRepository.findAllByUserId(customer.getId());
        assertEquals(threads, addresses.size());
        // Every thread saw "no addresses exist" and tried to set the
        // default; the user-row lock means only the first commit could.
        assertEquals(1L, addresses.stream().filter(Address::isDefaultAddress).count(),
                "exactly one of the simultaneously created addresses may be the default: "
                        + defaults(addresses));
    }

    // ------------------------------------------------------------------
    // Competing explicit default switches
    // ------------------------------------------------------------------

    @Test
    void competingDefaultSwitchesLeaveExactlyOneDefault() throws Exception {
        Address first = createAddress("First");
        Address second = createAddress("Second");
        int threads = 8;

        List<Throwable> failures = runConcurrently(threads, index -> {
            // Half the threads set the default on the first address,
            // half on the second, all at the same instant.
            Long target = index % 2 == 0 ? first.getId() : second.getId();
            addressService.update(customer.getEmail(), target, AddressRequest.builder()
                    .label("Renamed " + index)
                    .line1("Updated street")
                    .serviceLocationId(serviceLocationId)
                    .defaultAddress(true)
                    .build());
        });

        assertNoFailures(failures);
        List<Address> addresses = addressRepository.findAllByUserId(customer.getId());
        assertEquals(2, addresses.size());
        assertEquals(1L, addresses.stream().filter(Address::isDefaultAddress).count(),
                "exactly one address may hold the default after competing switches: "
                        + defaults(addresses));
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /**
     * Releases {@code threads} real threads simultaneously and returns
     * whatever they threw. Sequential calls would not exercise the lock
     * at all: each would see the previous one's committed rows, which is
     * also why "it works when run one after another" is not evidence
     * that a lock is present.
     */
    private List<Throwable> runConcurrently(int threads, ThrowingTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    task.run(index);
                } catch (Throwable thrown) {
                    failures.add(thrown);
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(30, TimeUnit.SECONDS), "worker threads did not come up");
        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "concurrent writes did not finish in time");
        pool.shutdownNow();
        return failures;
    }

    private void assertNoFailures(List<Throwable> failures) {
        assertTrue(failures.isEmpty(), () -> "concurrent writes failed: " + failures);
    }

    private String defaults(List<Address> addresses) {
        List<Long> ids = new ArrayList<>();
        for (Address address : addresses) {
            if (address.isDefaultAddress()) {
                ids.add(address.getId());
            }
        }
        return ids.toString();
    }

    @FunctionalInterface
    private interface ThrowingTask {
        void run(int index) throws Exception;
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private User createCustomer() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email("address-concurrency-" + UUID.randomUUID() + "@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Address Concurrency")
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        return userRepository.saveAndFlush(user);
    }

    private Address createAddress(String label) {
        return toEntity(addressService.create(customer.getEmail(), AddressRequest.builder()
                .label(label)
                .line1("1 Example Street")
                .serviceLocationId(serviceLocationId)
                .build()));
    }

    private Address toEntity(AddressResponse response) {
        return addressRepository.findById(response.getId()).orElseThrow();
    }
}
