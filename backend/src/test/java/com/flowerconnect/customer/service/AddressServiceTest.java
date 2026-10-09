package com.flowerconnect.customer.service;

import com.flowerconnect.customer.domain.Address;
import com.flowerconnect.customer.dto.AddressPageResponse;
import com.flowerconnect.customer.dto.AddressRequest;
import com.flowerconnect.customer.dto.AddressResponse;
import com.flowerconnect.customer.mapper.AddressMapper;
import com.flowerconnect.customer.repository.AddressRepository;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the address-book invariants (plan task 4.1).
 *
 * <p>A mocked repository cannot prove the SQL inside the queries
 * or the lock — those are covered by
 * {@code AddressApiIntegrationTest} and
 * {@code AddressConcurrencyIntegrationTest} against real MySQL.
 * What it can prove is the protocol around them: which query each
 * path asks for, that the first address becomes the default, that
 * setting a default clears the previous one, that deleting the
 * default promotes the oldest remaining address, and that the
 * coordinates always come from the service location's centroid.
 */
@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CUSTOMER_EMAIL = "customer@test.com";
    private static final Long KORAMANGALA_ID = 10L;
    private static final Long INDIRANAGAR_ID = 11L;

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceLocationRepository serviceLocationRepository;
    @Mock
    private AddressMapper mapper;

    private AddressService addressService;

    private User customer;
    private ServiceLocation koramangala;
    private ServiceLocation indiranagar;

    @BeforeEach
    void setUp() {
        addressService = new AddressService(
                addressRepository, userRepository, serviceLocationRepository, mapper);

        Role customerRole = Role.builder().id(1L).name("CUSTOMER").build();
        customer = User.builder()
                .id(USER_ID)
                .email(CUSTOMER_EMAIL)
                .fullName("Test Customer")
                .role(customerRole)
                .build();

        koramangala = ServiceLocation.builder()
                .id(KORAMANGALA_ID)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .build();

        indiranagar = ServiceLocation.builder()
                .id(INDIRANAGAR_ID)
                .city("Bengaluru")
                .area("Indiranagar")
                .pincode("560038")
                .latitude(new BigDecimal("12.97840000"))
                .longitude(new BigDecimal("77.64080000"))
                .build();

        lenient().when(userRepository.findByEmailWithRole(CUSTOMER_EMAIL))
                .thenReturn(Optional.of(customer));
        // Write paths lock the caller's row with a single locking
        // read (see AddressService.lockUser); read paths resolve the
        // caller without a lock.
        lenient().when(userRepository.findByEmailForUpdate(CUSTOMER_EMAIL))
                .thenReturn(Optional.of(customer));
    }

    // ------------------------------------------------------------------
    // Create: first-address and default rules
    // ------------------------------------------------------------------

    @Test
    void createFirstAddressBecomesDefaultAutomatically() {
        when(addressRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> {
                    Address saved = invocation.getArgument(0);
                    saved.setId(100L);
                    return saved;
                });
        when(mapper.toResponse(any(Address.class)))
                .thenReturn(AddressResponse.builder().id(100L).defaultAddress(true).build());

        AddressResponse response = addressService.create(CUSTOMER_EMAIL,
                AddressRequest.builder()
                        .label("Home")
                        .line1("1 Example Street")
                        .serviceLocationId(KORAMANGALA_ID)
                        .build());

        assertThat(response.isDefaultAddress()).isTrue();
        Address saved = captureSavedAddress();
        assertThat(saved.isDefaultAddress()).isTrue();
    }

    /**
     * The first address is the default whatever the request says:
     * a customer who has one address must be able to receive at
     * it, so an explicit {@code false} on the first address is
     * not honoured.
     */
    @Test
    void createFirstAddressBecomesDefaultEvenWhenNotRequested() {
        when(addressRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.create(CUSTOMER_EMAIL, AddressRequest.builder()
                .label("Home")
                .line1("1 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .defaultAddress(false)
                .build());

        assertThat(captureSavedAddress().isDefaultAddress()).isTrue();
    }

    @Test
    void createSubsequentAddressWithoutDefaultPreservesCurrentDefault() {
        Address currentDefault = address(10L, true);
        when(addressRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(currentDefault));
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.create(CUSTOMER_EMAIL, AddressRequest.builder()
                .label("Work")
                .line1("2 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .build());

        assertThat(captureSavedAddress().isDefaultAddress()).isFalse();
        assertThat(currentDefault.isDefaultAddress())
                .as("the existing default is untouched")
                .isTrue();
    }

    @Test
    void createSubsequentAddressWithExplicitDefaultClearsPreviousDefault() {
        Address currentDefault = address(10L, true);
        when(addressRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(currentDefault));
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.create(CUSTOMER_EMAIL, AddressRequest.builder()
                .label("Work")
                .line1("2 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .defaultAddress(true)
                .build());

        assertThat(captureSavedAddress().isDefaultAddress()).isTrue();
        assertThat(currentDefault.isDefaultAddress())
                .as("the previous default is cleared in the same transaction")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Create: centroid copying
    // ------------------------------------------------------------------

    @Test
    void createCopiesCentroidFromTheServiceLocation() {
        when(addressRepository.findAllByUserId(USER_ID)).thenReturn(List.of());
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.create(CUSTOMER_EMAIL, AddressRequest.builder()
                .label("Home")
                .line1("1 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .build());

        Address saved = captureSavedAddress();
        assertThat(saved.getLatitude()).isEqualByComparingTo(koramangala.getLatitude());
        assertThat(saved.getLongitude()).isEqualByComparingTo(koramangala.getLongitude());
    }

    @Test
    void createRejectsAnUnknownServiceLocation() {
        when(serviceLocationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.create(CUSTOMER_EMAIL,
                AddressRequest.builder()
                        .label("Home")
                        .line1("1 Example Street")
                        .serviceLocationId(999L)
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown service location");
    }

    // ------------------------------------------------------------------
    // Update: full replacement and default handling
    // ------------------------------------------------------------------

    @Test
    void updateReplacesEveryEditableField() {
        Address stored = address(10L, true);
        stored.setLabel("Old label");
        stored.setLine1("Old line 1");
        stored.setLine2("Old line 2");
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(stored));
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // line2 omitted by the client is a null in a full
        // replacement, so it clears the stored value.
        addressService.update(CUSTOMER_EMAIL, 10L, AddressRequest.builder()
                .label("New label")
                .line1("New line 1")
                .serviceLocationId(KORAMANGALA_ID)
                .build());

        assertThat(stored.getLabel()).isEqualTo("New label");
        assertThat(stored.getLine1()).isEqualTo("New line 1");
        assertThat(stored.getLine2()).isNull();
        assertThat(stored.getServiceLocation().getId()).isEqualTo(KORAMANGALA_ID);
        // Ordinary field changes must not touch the default flag.
        assertThat(stored.isDefaultAddress()).isTrue();
    }

    @Test
    void updateChangingServiceLocationRecopiesTheNewCentroid() {
        Address stored = address(10L, false);
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(stored));
        when(serviceLocationRepository.findById(INDIRANAGAR_ID))
                .thenReturn(Optional.of(indiranagar));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.update(CUSTOMER_EMAIL, 10L, AddressRequest.builder()
                .label("Home")
                .line1("1 Example Street")
                .serviceLocationId(INDIRANAGAR_ID)
                .build());

        assertThat(stored.getServiceLocation().getId()).isEqualTo(INDIRANAGAR_ID);
        assertThat(stored.getLatitude()).isEqualByComparingTo(indiranagar.getLatitude());
        assertThat(stored.getLongitude()).isEqualByComparingTo(indiranagar.getLongitude());
    }

    @Test
    void updateWithOmittedDefaultKeepsTheStoredFlag() {
        Address stored = address(10L, true);
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(stored));
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.update(CUSTOMER_EMAIL, 10L, AddressRequest.builder()
                .label("Home")
                .line1("1 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .build());

        assertThat(stored.isDefaultAddress()).isTrue();
        verify(addressRepository, never()).findAllByUserId(anyLong());
    }

    @Test
    void updateSettingAnotherAddressDefaultClearsThePreviousDefault() {
        Address previousDefault = address(10L, true);
        Address beingUpdated = address(20L, false);
        when(addressRepository.findByIdAndUserId(20L, USER_ID))
                .thenReturn(Optional.of(beingUpdated));
        when(addressRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(previousDefault, beingUpdated));
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.update(CUSTOMER_EMAIL, 20L, AddressRequest.builder()
                .label("Work")
                .line1("2 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .defaultAddress(true)
                .build());

        assertThat(beingUpdated.isDefaultAddress()).isTrue();
        assertThat(previousDefault.isDefaultAddress()).isFalse();
    }

    @Test
    void updateExplicitlyClearingTheDefaultLeavesNoDefault() {
        Address stored = address(10L, true);
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(stored));
        when(serviceLocationRepository.findById(KORAMANGALA_ID))
                .thenReturn(Optional.of(koramangala));
        when(addressRepository.saveAndFlush(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        addressService.update(CUSTOMER_EMAIL, 10L, AddressRequest.builder()
                .label("Home")
                .line1("1 Example Street")
                .serviceLocationId(KORAMANGALA_ID)
                .defaultAddress(false)
                .build());

        assertThat(stored.isDefaultAddress()).isFalse();
        // Demotion is not a delete: no promotion happens here.
        verify(addressRepository, never()).findFirstByUserIdOrderByIdAsc(anyLong());
    }

    @Test
    void updateRejectsAnAddressBelongingToAnotherCustomer() {
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.update(CUSTOMER_EMAIL, 10L,
                AddressRequest.builder()
                        .label("Home")
                        .line1("1 Example Street")
                        .serviceLocationId(KORAMANGALA_ID)
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Address not found");
    }

    @Test
    void updateRejectsAnUnknownServiceLocation() {
        Address stored = address(10L, false);
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(stored));
        when(serviceLocationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.update(CUSTOMER_EMAIL, 10L,
                AddressRequest.builder()
                        .label("Home")
                        .line1("1 Example Street")
                        .serviceLocationId(999L)
                        .build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown service location");
    }

    // ------------------------------------------------------------------
    // Delete: promotion rules
    // ------------------------------------------------------------------

    @Test
    void deleteDefaultPromotesTheOldestRemainingAddress() {
        Address defaultAddress = address(10L, true);
        Address oldest = address(20L, false);
        Address newest = address(30L, false);
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(defaultAddress));
        when(addressRepository.findFirstByUserIdOrderByIdAsc(USER_ID))
                .thenReturn(Optional.of(oldest));

        addressService.delete(CUSTOMER_EMAIL, 10L);

        verify(addressRepository).delete(defaultAddress);
        assertThat(oldest.isDefaultAddress())
                .as("the oldest remaining address is promoted")
                .isTrue();
        assertThat(newest.isDefaultAddress()).isFalse();
    }

    @Test
    void deleteNonDefaultPreservesTheCurrentDefault() {
        Address nonDefault = address(20L, false);
        when(addressRepository.findByIdAndUserId(20L, USER_ID))
                .thenReturn(Optional.of(nonDefault));

        addressService.delete(CUSTOMER_EMAIL, 20L);

        verify(addressRepository).delete(nonDefault);
        // No promotion lookup happens at all when the deleted
        // row was not the default.
        verify(addressRepository, never()).findFirstByUserIdOrderByIdAsc(anyLong());
    }

    @Test
    void deleteOnlyAddressLeavesNoDefault() {
        Address only = address(10L, true);
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(only));
        when(addressRepository.findFirstByUserIdOrderByIdAsc(USER_ID))
                .thenReturn(Optional.empty());

        addressService.delete(CUSTOMER_EMAIL, 10L);

        verify(addressRepository).delete(only);
        // Nothing to promote: the customer has no default, which
        // is a representable state until the next address is added.
    }

    @Test
    void deleteRejectsAnAddressBelongingToAnotherCustomer() {
        when(addressRepository.findByIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.delete(CUSTOMER_EMAIL, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Address not found");
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    @Test
    void listPagesTheCallersAddressesInTheFixedOrder() {
        Address first = address(10L, true);
        Address second = address(20L, false);
        PageRequest pageable = PageRequest.of(0, 20);
        when(addressRepository.findByUserId(eq(USER_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        // The service maps each row with the single-address
        // method, so consecutive returns feed the two rows.
        when(mapper.toResponse(any(Address.class)))
                .thenReturn(AddressResponse.builder().id(10L).defaultAddress(true).build(),
                        AddressResponse.builder().id(20L).defaultAddress(false).build());

        AddressPageResponse response = addressService.list(CUSTOMER_EMAIL, 0, 20);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.isLast()).isTrue();
        assertThat(response.isEmpty()).isFalse();
        // The fixed default-first-then-id order lives in the
        // repository query, not in a caller-supplied sort.
        verify(addressRepository).findByUserId(eq(USER_ID), eq(pageable));
    }

    @Test
    void listClampsPageIndexAndPageSize() {
        // An empty page: the mapper is never invoked.
        when(addressRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        addressService.list(CUSTOMER_EMAIL, -5, 0);
        verify(addressRepository).findByUserId(eq(USER_ID), eq(PageRequest.of(0, 1)));

        addressService.list(CUSTOMER_EMAIL, 0, 1000);
        verify(addressRepository).findByUserId(eq(USER_ID), eq(PageRequest.of(0, 100)));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Address address(long id, boolean isDefault) {
        return Address.builder()
                .id(id)
                .user(customer)
                .label("Address " + id)
                .line1(id + " Example Street")
                .serviceLocation(koramangala)
                .latitude(koramangala.getLatitude())
                .longitude(koramangala.getLongitude())
                .defaultAddress(isDefault)
                .build();
    }

    private Address captureSavedAddress() {
        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }
}
