package org.aldousdev.dockflowbackend.auth.repository;

import org.aldousdev.dockflowbackend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    @Query("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.memberships m LEFT JOIN FETCH m.company LEFT JOIN FETCH m.role WHERE u.email = :email")
    Optional<User> findByEmailWithMemberships(@Param("email") String email);

    Optional<User> findByRefreshToken(String refreshToken);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.memberships m LEFT JOIN FETCH m.company LEFT JOIN FETCH m.role WHERE u.refreshToken = :refreshToken")
    Optional<User> findByRefreshTokenWithMemberships(@Param("refreshToken") String refreshToken);
}
