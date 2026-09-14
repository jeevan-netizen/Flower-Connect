package com.flowerconnect.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.mapper.UserMapper;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.UserResponse;
import jakarta.validation.constraints.Null;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserController(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return ResponseEntity.ok(userMapper.toResponse(user));
    }
}
