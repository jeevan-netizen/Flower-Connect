package com.flowerconnect.vendor.security;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the approval rule itself (plan task 2.7). The HTTP-level
 * behaviour — status codes, error envelope, dynamic admin transitions — is
 * covered by {@code VendorApprovalGatingIntegrationTest} against real MySQL.
 */
class VendorApprovalGuardTest {

    private static final String EMAIL = "florist@test.com";

    private final VendorProfileRepository vendorProfileRepository = mock(VendorProfileRepository.class);
    private final VendorApprovalGuard guard = new VendorApprovalGuard(vendorProfileRepository);

    // ------------------------------------------------------------------ allowed

    @Test
    void anApprovedVendorPasses() {
        givenStatus(VendorProfile.Status.APPROVED);

        assertTrue(guard.isApproved(vendor(EMAIL)));
    }

    // -------------------------------------------------------------- role checks

    @Test
    void aCustomerIsRefusedWithoutTouchingTheDatabase() {
        VendorNotApprovedException ex = assertThrows(VendorNotApprovedException.class,
                () -> guard.isApproved(authentication("customer@test.com", "CUSTOMER")));

        assertEquals(VendorApprovalGuard.MESSAGE_NO_VENDOR_ROLE, ex.getMessage());
        verify(vendorProfileRepository, never()).findByUserEmail(anyString());
    }

    @Test
    void anAdminIsNotTreatedAsAVendor() {
        assertThrows(VendorNotApprovedException.class,
                () -> guard.isApproved(authentication("admin@test.com", "ADMIN")));

        verify(vendorProfileRepository, never()).findByUserEmail(anyString());
    }

    @Test
    void anUnauthenticatedCallerIsRefused() {
        assertThrows(VendorNotApprovedException.class, () -> guard.isApproved(null));
        assertFalse(guard.isVendor(null));
    }

    // ------------------------------------------------------------ status matrix

    @ParameterizedTest
    @EnumSource(value = VendorProfile.Status.class, names = {"PENDING_APPROVAL", "REJECTED", "SUSPENDED"})
    void everyNonApprovedStatusIsRefused(VendorProfile.Status status) {
        givenStatus(status);

        assertThrows(VendorNotApprovedException.class, () -> guard.isApproved(vendor(EMAIL)));
    }

    @Test
    void eachNonApprovedStatusCarriesItsOwnMessage() {
        assertEquals(VendorApprovalGuard.MESSAGE_PENDING, messageFor(VendorProfile.Status.PENDING_APPROVAL));
        assertEquals(VendorApprovalGuard.MESSAGE_REJECTED, messageFor(VendorProfile.Status.REJECTED));
        assertEquals(VendorApprovalGuard.MESSAGE_SUSPENDED, messageFor(VendorProfile.Status.SUSPENDED));
    }

    // ------------------------------------------------------------ missing profile

    @Test
    void aVendorAccountWithoutAProfileIsRefused() {
        when(vendorProfileRepository.findByUserEmail(EMAIL)).thenReturn(Optional.empty());

        VendorNotApprovedException ex = assertThrows(VendorNotApprovedException.class,
                () -> guard.isApproved(vendor(EMAIL)));

        assertEquals(VendorApprovalGuard.MESSAGE_NO_PROFILE, ex.getMessage());
    }

    // ------------------------------------------------------------------- helpers

    private void givenStatus(VendorProfile.Status status) {
        when(vendorProfileRepository.findByUserEmail(EMAIL))
                .thenReturn(Optional.of(VendorProfile.builder()
                        .id(1L)
                        .businessName("Test Blossoms")
                        .status(status)
                        .build()));
    }

    private String messageFor(VendorProfile.Status status) {
        givenStatus(status);
        return assertThrows(VendorNotApprovedException.class, () -> guard.isApproved(vendor(EMAIL)))
                .getMessage();
    }

    private static Authentication vendor(String email) {
        return authentication(email, "FLORIST");
    }

    private static Authentication authentication(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new UsernamePasswordAuthenticationToken(email, null, authorities);
    }
}
