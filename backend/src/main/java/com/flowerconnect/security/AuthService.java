package com.flowerconnect.security;

import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.AccountSuspendedException;
import com.flowerconnect.exception.ResourceConflictException;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.*;
import com.flowerconnect.security.jwt.JwtService;
import com.flowerconnect.security.jwt.RefreshTokenService;
import com.flowerconnect.security.jwt.UserDetailsImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.flowerconnect.security.util.EmailNormalizer;

import java.util.List;

@Slf4j
@Service
public class AuthService {

    private static final String AUTH_ERROR_MESSAGE = "Invalid email or password";
    private static final String ACCOUNT_SUSPENDED_MESSAGE = "Account suspended";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            JwtProperties jwtProperties,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());
        String normalizedPhone = request.getPhone() != null ? request.getPhone().trim() : null;

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResourceConflictException("Email already in use");
        }

        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new ResourceConflictException("Phone number already in use");
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role not found"));

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(normalizedPhone)
                .role(customerRole)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.save(user);

        return createAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword());
            Authentication authentication = authenticationManager.authenticate(authToken);
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

            User user = userRepository.findByIdWithRole(userDetails.getId())
                    .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

            // Check status after successful password verification
            if (user.getStatus() == User.Status.SUSPENDED) {
                throw new AccountSuspendedException(ACCOUNT_SUSPENDED_MESSAGE);
            }
            if (user.getStatus() == User.Status.DISABLED) {
                throw new BadCredentialsException(AUTH_ERROR_MESSAGE);
            }

            return createAuthResponse(user);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException(AUTH_ERROR_MESSAGE);
        }
    }

    public AuthResponse refresh(String rawToken) {
        User user = refreshTokenService.validateAndReturnUser(rawToken);
        String newRefreshToken = refreshTokenService.rotateRefreshToken(rawToken);

        String roleName = user.getRole() != null ? user.getRole().getName() : "CUSTOMER";
        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                roleName,
                List.of("ROLE_" + roleName));

        return AuthResponse.of(accessToken, newRefreshToken, jwtProperties.getAccessTtlMs());
    }

    public void logout(String rawToken) {
        refreshTokenService.revokeRefreshToken(rawToken);
    }

    private AuthResponse createAuthResponse(User user) {
        String roleName = user.getRole() != null ? user.getRole().getName() : "CUSTOMER";
        List<String> authorities = List.of("ROLE_" + roleName);
        String accessToken = jwtService.generateAccessToken(user.getEmail(), roleName, authorities);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponse.of(accessToken, refreshToken, jwtProperties.getAccessTtlMs());
    }
}
