package com.flowerconnect.repository;

import com.flowerconnect.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
    Optional<User> findByIdWithRole(Long id);
}
