package com.flowerconnect.customer.service;

import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.dto.AddressPageResponse;
import com.flowerconnect.customer.dto.AddressRequest;
import com.flowerconnect.customer.dto.AddressResponse;
import com.flowerconnect.customer.mapper.AddressMapper;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Customer address book (plan task 4.1).
 *
 * <p><b>Ownership.</b> Every method resolves the caller from the JWT
 * subject (the email on the authentication), never from a request
 * parameter or body. An address is "the caller's" when its
 * {@code user_id} matches the caller's id; a foreign id and a
 * missing id are both a 404, because "yours" is defined by the
 * principal (the {@code requireOwnedProduct} precedent).
 *
 * <p><b>Centroid copying (D-4).</b> {@code latitude}/{@code longitude}
 * are copied from the selected {@link ServiceLocation}'s centroid on
 * every write and are never read from the request — the request DTO
 * does not carry them. A {@code PUT} that changes the service
 * location recopies the new centroid in the same transaction.
 *
 * <p><b>Locking strategy.</b> Every write method is one
 * {@code @Transactional} whose <em>first</em> read locks the owning
 * user row with {@code SELECT ... FOR UPDATE}
 * ({@code UserRepository.findByEmailForUpdate}), and only then loads
 * and mutates that user's addresses. The user row — not the address
 * rows — is the serialization point, because a customer who is creating
 * their <em>first</em> address has no address row to lock: locking
 * address rows alone would let two simultaneous first-address creations
 * both observe "no addresses exist" and both set the default flag.
 * Locking the user row makes every default-mutating transaction for one
 * customer serialise in a fixed order.
 *
 * <p>The lock is the first read, not the last, which inverts the D-24
 * discipline on purpose. Under MySQL REPEATABLE READ, the first
 * <em>plain</em> read of a transaction establishes its snapshot; a
 * plain resolve-then-lock sequence would snapshot the address table
 * <em>before</em> the lock is taken, and the later "does a default
 * already exist" read would then miss rows a concurrent transaction
 * committed in between — which is precisely the race the lock exists
 * to close. A locking read reads the latest committed data and
 * establishes no snapshot, so the snapshot is created under the lock
 * and every plain read in the transaction sees everything committed
 * before it. Inverting D-24 is safe here because the locked row is
 * always the caller's own user row, resolved from the JWT subject: no
 * caller can name another user's row, so the foreign-row stall attack
 * D-24's ordering guards against cannot happen. Create, set-default and
 * delete-with-promotion all take the same lock on the same row, so they
 * cannot interleave.
 *
 * <p><b>Default-address invariants</b> (all enforced inside that
 * locked transaction):
 * <ol>
 *   <li>the first address for a customer becomes the default
 *       automatically, whatever the request says;</li>
 *   <li>a subsequent address is default only when the request
 *       explicitly says so, and setting one clears the previous
 *       default;</li>
 *   <li>deleting a non-default address leaves the default alone;</li>
 *   <li>deleting the default promotes the oldest remaining address
 *       ({@code MIN(id)}); deleting the only address leaves the
 *       customer with no default;</li>
 *   <li>an ordinary field update never changes the stored default
 *       flag unless the request explicitly sets it.</li>
 * </ol>
 *
 * <p>There is deliberately no database constraint behind these rules
 * (MySQL has no partial unique index — the D-21 finding), so this
 * service is the sole enforcement point for every writer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final AddressMapper mapper;

    /**
     * The caller's addresses, one page: the default address first,
     * then the rest in ascending id order. Page size is clamped to
     * {@code 1..100} and the page index to {@code >= 0}, matching
     * the catalog and admin listing services.
     */
    @Transactional(readOnly = true)
    public AddressPageResponse list(String customerEmail, int page, int size) {
        User user = requireUser(customerEmail);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<Address> result = addressRepository.findByUserId(
                user.getId(), PageRequest.of(safePage, safeSize));
        return toPageResponse(result);
    }

    /**
     * One of the caller's own addresses. A missing id and an id
     * belonging to another customer are both a 404.
     */
    @Transactional(readOnly = true)
    public AddressResponse getById(String customerEmail, Long addressId) {
        User user = requireUser(customerEmail);
        return mapper.toResponse(requireOwnedAddress(user.getId(), addressId));
    }

    /**
     * Creates an address for the caller. The service location is
     * resolved (unknown id → 400) and its centroid copied onto the
     * new row. The first address for a customer becomes the default
     * automatically; a later address takes the default only when the
     * request explicitly asks for it, in which case the previous
     * default is cleared within the same locked transaction.
     */
    @Transactional
    public AddressResponse create(String customerEmail, AddressRequest request) {
        // Lock the owning user row first: this is the serialization
        // point for every default-address mutation, and it is what
        // makes simultaneous first-address creation safe (there is no
        // address row to lock yet). See the class javadoc.
        User user = lockUser(customerEmail);
        ServiceLocation location = resolveServiceLocation(request.getServiceLocationId());

        List<Address> existing = addressRepository.findAllByUserId(user.getId());
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.getDefaultAddress());
        if (makeDefault) {
            // Setting a new default clears the previous one, inside
            // the same locked transaction.
            existing.forEach(address -> address.setDefaultAddress(false));
        }

        Address address = Address.builder()
                .user(user)
                .label(request.getLabel())
                .line1(request.getLine1())
                .line2(request.getLine2())
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .defaultAddress(makeDefault)
                .build();

        addressRepository.saveAndFlush(address);
        log.info("Created address {} for user {}", address.getId(), user.getId());
        return mapper.toResponse(address);
    }

    /**
     * Full replacement (the D-15 precedent) of an address the caller
     * owns: every editable field is replaced from the request, an
     * omitted {@code line2} clears it, and the centroid of the
     * (possibly new) service location is recopied in this same
     * transaction. An omitted {@code defaultAddress} keeps the stored
     * flag, so an ordinary edit cannot silently change which address
     * is the default; an explicit value is applied, and setting a
     * new default clears the previous one.
     */
    @Transactional
    public AddressResponse update(String customerEmail, Long addressId, AddressRequest request) {
        User user = lockUser(customerEmail);
        Address address = requireOwnedAddress(user.getId(), addressId);
        ServiceLocation location = resolveServiceLocation(request.getServiceLocationId());

        address.setLabel(request.getLabel());
        address.setLine1(request.getLine1());
        address.setLine2(request.getLine2());
        address.setServiceLocation(location);
        // Coordinates are server-derived only: always recopied from
        // the resolved location, never trusted from the client.
        address.setLatitude(location.getLatitude());
        address.setLongitude(location.getLongitude());

        if (request.getDefaultAddress() != null) {
            applyDefault(user.getId(), address, request.getDefaultAddress());
        }

        addressRepository.saveAndFlush(address);
        log.info("Updated address {} for user {}", addressId, user.getId());
        return mapper.toResponse(address);
    }

    /**
     * Deletes an address the caller owns. Deleting the default
     * promotes the oldest remaining address ({@code MIN(id)}) within
     * the same locked transaction; deleting the only address leaves
     * the customer with no default; deleting a non-default address
     * leaves the default untouched.
     */
    @Transactional
    public void delete(String customerEmail, Long addressId) {
        User user = lockUser(customerEmail);
        Address address = requireOwnedAddress(user.getId(), addressId);
        boolean wasDefault = address.isDefaultAddress();

        addressRepository.delete(address);

        if (wasDefault) {
            // Promotion happens inside the lock and the same
            // transaction as the delete, so a failure rolls both
            // back together: the customer is never left with two
            // defaults or with none while addresses remain.
            addressRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                    .ifPresent(promoted -> promoted.setDefaultAddress(true));
        }
        log.info("Deleted address {} for user {}", addressId, user.getId());
    }

    // ------------------------------------------------------------------
    // Lookups and ownership
    // ------------------------------------------------------------------

    /**
     * Resolves the authenticated user by email. The authentication
     * principal is the only source of the caller's identity.
     */
    private User requireUser(String customerEmail) {
        return userRepository.findByEmailWithRole(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * Resolves the authenticated user and locks their row with
     * {@code SELECT ... FOR UPDATE} for the rest of the transaction.
     * Every default-mutating method takes this lock before touching
     * any address row — including when the customer has no address
     * rows yet, which is exactly the case an address-row lock cannot
     * cover. See the class javadoc for the full protocol, and for why
     * the lock is the first read rather than the last.
     */
    private User lockUser(String customerEmail) {
        return userRepository.findByEmailForUpdate(customerEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    /**
     * The customer's own address by id, or 404. Because the query is
     * scoped by {@code user_id}, an address belonging to another
     * customer and a nonexistent address are the same empty result —
     * the caller cannot distinguish "someone else's" from "missing",
     * which is the point.
     */
    private Address requireOwnedAddress(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> BusinessException.notFound("Address not found"));
    }

    /**
     * Resolves a service location for assignment, refusing an unknown
     * id with a 400 — the {@code VendorService.resolveServiceLocation}
     * precedent. The location's centroid is the only source of the
     * address coordinates (D-4).
     */
    private ServiceLocation resolveServiceLocation(Long serviceLocationId) {
        if (serviceLocationId == null) {
            throw BusinessException.badRequest("Service location is required");
        }
        return serviceLocationRepository.findById(serviceLocationId)
                .orElseThrow(() -> BusinessException.badRequest("Unknown service location"));
    }

    /**
     * Applies an explicit default request on update: setting a new
     * default clears every other address's flag for this customer;
     * clearing the default simply clears it (promotion on demotion is
     * a delete-time rule, not an update-time one).
     */
    private void applyDefault(Long userId, Address address, boolean makeDefault) {
        if (makeDefault) {
            addressRepository.findAllByUserId(userId).stream()
                    .filter(other -> !other.getId().equals(address.getId()))
                    .forEach(other -> other.setDefaultAddress(false));
            address.setDefaultAddress(true);
        } else {
            address.setDefaultAddress(false);
        }
    }

    private AddressPageResponse toPageResponse(Page<Address> result) {
        return AddressPageResponse.builder()
                .content(result.getContent().stream().map(mapper::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }
}
