package com.flowerconnect.vendor;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.security.RequiresApprovedVendor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>Stand-in for a Phase 3 vendor-feature endpoint, used to exercise approval
 * gating (plan task 2.7) before any catalog or order route exists.
 *
 * <p>It sits under {@code /api/v1/vendors/**} on purpose, so the production role
 * matcher {@code hasRole("FLORIST")} runs first, exactly as it will for the real
 * Phase 3 routes. That ordering is what makes the plan's matrix observable: a
 * CUSTOMER or ADMIN is stopped by RBAC with {@code FORBIDDEN} and never reaches
 * the approval guard, while a FLORIST passes RBAC and is then judged on approval
 * with {@code VENDOR_NOT_APPROVED}.
 *
 * <p>Two routes on purpose: the {@code /guarded} ones carry
 * {@link RequiresApprovedVendor} and {@code /unguarded} does not, so the tests
 * can show that gating is a per-handler decision rather than a blanket rule over
 * the vendor namespace. No route accepts a vendor identifier — the profile is
 * always resolved from the JWT subject.
 *
 * <p>Profile-scoped so it is only active in the gating integration test.
 */
@Profile("vendor-approval-probe")
@RestController
@RequestMapping("/api/v1/vendors/test-features")
@RequiredArgsConstructor
public class TestVendorFeatureController {

    private final VendorProfileRepository vendorProfileRepository;

    @GetMapping("/guarded")
    @RequiresApprovedVendor
    public ResponseEntity<Map<String, Object>> guardedRead(Authentication authentication) {
        return ResponseEntity.ok(describe(authentication));
    }

    @PostMapping("/guarded")
    @RequiresApprovedVendor
    public ResponseEntity<Map<String, Object>> guardedWrite(Authentication authentication) {
        return ResponseEntity.ok(describe(authentication));
    }

    @GetMapping("/unguarded")
    public ResponseEntity<Map<String, Object>> unguarded(Authentication authentication) {
        return ResponseEntity.ok(describe(authentication));
    }

    private Map<String, Object> describe(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", authentication.getName());
        body.put("status", vendorProfileRepository.findByUserEmail(authentication.getName())
                .map(VendorProfile::getStatus)
                .map(Enum::name)
                .orElse(null));
        return body;
    }
}
