package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    /**
     * Find user by username with eager loading of profiles and roles
     * This prevents lazy loading issues when accessing authorities
     * Modified to use a simpler fetch strategy to avoid ConcurrentModificationException
     */
    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN FETCH u.userProfiles up " +
           "LEFT JOIN FETCH up.profile p " +
           "LEFT JOIN FETCH p.profileRoles pr " +
           "LEFT JOIN FETCH pr.role " +
           "LEFT JOIN FETCH u.refreshTokensSet " +
           "WHERE u.username = :username")
    Optional<User> findByUsernameWithProfiles(@Param("username") String username);
} 