package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Profile;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Model.UserProfile;
import com.example.DataPreparationApp.Model.UserProfileKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UserProfileKey> {
    List<UserProfile> findByUser(User user);
    List<UserProfile> findByProfile(Profile profile);
    
    @Modifying
    @Query("DELETE FROM UserProfile up WHERE up.id.userId = :userId AND up.id.profileId = :profileId")
    void deleteByUserIdAndProfileId(UUID userId, UUID profileId);
    
    boolean existsByUserAndProfile(User user, Profile profile);
    
    /**
     * Safely find user profiles without triggering lazy loading issues
     */
    @Query("SELECT up FROM UserProfile up JOIN FETCH up.profile WHERE up.id.userId = :userId")
    List<UserProfile> findByUserId(@Param("userId") UUID userId);
} 