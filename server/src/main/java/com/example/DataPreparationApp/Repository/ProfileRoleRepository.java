package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Profile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.example.DataPreparationApp.Model.ProfileRoleKey;
import com.example.DataPreparationApp.Model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProfileRoleRepository extends JpaRepository<ProfileRole, ProfileRoleKey> {
    List<ProfileRole> findByProfile(Profile profile);
    List<ProfileRole> findByRole(Role role);
    
    @Modifying
    @Query("DELETE FROM ProfileRole pr WHERE pr.profile.id = :profileId AND pr.role.id = :roleId")
    void deleteByProfileIdAndRoleId(UUID profileId, UUID roleId);
    
    boolean existsByProfileAndRole(Profile profile, Role role);
    
    /**
     * Safely find profile roles without triggering lazy loading issues
     */
    @Query("SELECT pr FROM ProfileRole pr JOIN FETCH pr.role WHERE pr.id.profileId = :profileId")
    List<ProfileRole> findByProfileId(@Param("profileId") UUID profileId);
} 