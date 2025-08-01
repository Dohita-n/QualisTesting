package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByName(String name);
    boolean existsByName(String name);
    
    /**
     * Custom query to load all profiles without eager loading relationships
     * This helps avoid ConcurrentModificationException
     */
    @Query("SELECT p FROM Profile p")
    List<Profile> findAllProfiles();
    
    /**
     * Find profile by id without eager loading relationships
     */
    @Query("SELECT p FROM Profile p WHERE p.id = :id")
    Optional<Profile> findByIdSimple(@Param("id") UUID id);
} 