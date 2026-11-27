package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.dto.FarmerProfileResponse;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves the authenticated farmer's own profile from the JWT subject. The
 * farmer's name, mobile number and address are all stored on the {@code users}
 * row, so this is a single read scoped to the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerProfileService {

    private final UserRepository userRepository;

    /**
     * Returns the profile for the authenticated farmer identified by email.
     * Throws 404 when the account does not exist and 403 when the caller is not
     * a farmer.
     */
    @Transactional(readOnly = true)
    public FarmerProfileResponse profile(String email) {
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> BusinessException.notFound("Farmer not found"));
        if (!"FARMER".equals(user.getRole().getName())) {
            throw BusinessException.forbidden("Only a farmer may view a farmer profile");
        }
        return FarmerProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .role(user.getRole().getName())
                .build();
    }
}
