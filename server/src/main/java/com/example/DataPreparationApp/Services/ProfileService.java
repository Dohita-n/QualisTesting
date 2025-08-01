package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.DTO.ProfileDTO;
import com.example.DataPreparationApp.DTO.RoleDTO;
import com.example.DataPreparationApp.DTO.UserDTO;
import com.example.DataPreparationApp.Model.*;
import com.example.DataPreparationApp.Repository.ProfileRepository;
import com.example.DataPreparationApp.Repository.ProfileRoleRepository;
import com.example.DataPreparationApp.Repository.RoleRepository;
import com.example.DataPreparationApp.Repository.UserProfileRepository;
import com.example.DataPreparationApp.Repository.UserRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class ProfileService {
    
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final AuthorityService authorityService;
    
    @Autowired
    public ProfileService(
            ProfileRepository profileRepository,
            RoleRepository roleRepository,
            ProfileRoleRepository profileRoleRepository,
            UserProfileRepository userProfileRepository,
            UserRepository userRepository,
            AuthorityService authorityService) {
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.profileRoleRepository = profileRoleRepository;
        this.userProfileRepository = userProfileRepository;
        this.userRepository = userRepository;
        this.authorityService = authorityService;
    }
    
    public List<ProfileDTO> getAllProfiles() {
        try {
            return profileRepository.findAllProfiles().stream()
                    .map(ProfileDTO::fromEntitySimplified)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    
    /**
     * Gets all profiles with their associated role names
     * This method avoids lazy loading issues by using direct queries 
     */
    @Transactional
    public List<ProfileDTO> getAllProfilesWithRoleNames() {
        try {
            List<Profile> profiles = profileRepository.findAllProfiles();
            List<ProfileDTO> profileDTOs = new ArrayList<>();
            
            for (Profile profile : profiles) {
                ProfileDTO profileDTO = ProfileDTO.fromEntitySimplified(profile);
                
                // Fetch role names directly using a dedicated query
                List<String> roleNames = new ArrayList<>();
                List<ProfileRole> profileRoles = profileRoleRepository.findByProfileId(profile.getId());
                
                for (ProfileRole profileRole : profileRoles) {
                    if (profileRole != null && profileRole.getRole() != null) {
                        roleNames.add(profileRole.getRole().getName());
                    }
                }
                
                profileDTO.setRoleNames(roleNames);
                profileDTOs.add(profileDTO);
            }
            
            return profileDTOs;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    
    public ProfileDTO getProfileById(UUID id) {
        try {
            Profile profile = profileRepository.findByIdSimple(id)
                    .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + id));
            
            ProfileDTO profileDTO = ProfileDTO.fromEntitySimplified(profile);
            
            // Fetch role names directly using a dedicated query
            List<String> roleNames = new ArrayList<>();
            List<ProfileRole> profileRoles = profileRoleRepository.findByProfileId(profile.getId());
            
            for (ProfileRole profileRole : profileRoles) {
                if (profileRole != null && profileRole.getRole() != null) {
                    roleNames.add(profileRole.getRole().getName());
                }
            }
            
            profileDTO.setRoleNames(roleNames);
            return profileDTO;
        } catch (EntityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error fetching profile: " + e.getMessage(), e);
        }
    }
    
    public ProfileDTO getProfileByName(String name) {
        Profile profile = profileRepository.findByName(name)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with name: " + name));
        return ProfileDTO.fromEntity(profile);
    }
    
    @Transactional
    public ProfileDTO createProfile(ProfileDTO profileDTO) {
        if (profileRepository.existsByName(profileDTO.getName())) {
            throw new EntityExistsException("Profile with name " + profileDTO.getName() + " already exists");
        }
        
        Profile profile = profileDTO.toEntity();
        Profile savedProfile = profileRepository.save(profile);
        
        // Check if we have roleIds or roles with ids
        List<UUID> roleIdsToAssign = new ArrayList<>();
        
        // Add roleIds if they are provided directly
        if (profileDTO.getRoleIds() != null && !profileDTO.getRoleIds().isEmpty()) {
            roleIdsToAssign.addAll(profileDTO.getRoleIds());
        }
        
        // IF profileDTO contains a lit of RoleDTO objects, their IDs are extracted & added to the roleIdsToAssign list
        if (profileDTO.getRoles() != null && !profileDTO.getRoles().isEmpty()) {
            List<UUID> roleIdsFromRoles = profileDTO.getRoles().stream()
                    .map(RoleDTO::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            roleIdsToAssign.addAll(roleIdsFromRoles);
        }
        
        // Assign roles if we have any IDs to assign
        if (!roleIdsToAssign.isEmpty()) {
            assignRolesToProfile(savedProfile.getId(), roleIdsToAssign);
        }
        
        return getProfileById(savedProfile.getId()); // Get fresh with roles
    }
    
    @Transactional
    public ProfileDTO updateProfile(UUID id, ProfileDTO profileDTO) {
        Profile existingProfile = profileRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + id));
        
        // Check if new name is already taken by another profile
        if (!existingProfile.getName().equals(profileDTO.getName()) && 
                profileRepository.existsByName(profileDTO.getName())) {
            throw new EntityExistsException("Profile with name " + profileDTO.getName() + " already exists");
        }
        
        existingProfile.setName(profileDTO.getName());
        existingProfile.setDescription(profileDTO.getDescription());
        
        Profile updatedProfile = profileRepository.save(existingProfile);
        return ProfileDTO.fromEntity(updatedProfile);
    }
    
    @Transactional
    public void deleteProfile(UUID id) {
        if (!profileRepository.existsById(id)) {
            throw new EntityNotFoundException("Profile not found with id: " + id);
        }
        profileRepository.deleteById(id);
    }
    
    @Transactional
    public ProfileDTO assignRolesToProfile(UUID profileId, List<UUID> roleIds) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + profileId));
        
        for (UUID roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + roleId));
            
            if (profileRoleRepository.existsByProfileAndRole(profile, role)) {
                continue; // Skip if already assigned
            }
            
            ProfileRoleKey key = new ProfileRoleKey();
            key.setProfileId(profileId);
            key.setRoleId(roleId);
            
            ProfileRole profileRole = new ProfileRole();
            profileRole.setId(key);
            profileRole.setProfile(profile);
            profileRole.setRole(role);
            
            profileRoleRepository.save(profileRole);
        }
        
        // Invalidate authority cache for all users with this profile
        invalidateAuthorityCacheForProfileUsers(profileId);
        
        return getProfileById(profileId); // Return fresh with roles
    }
    
    @Transactional
    public ProfileDTO removeRolesFromProfile(UUID profileId, List<UUID> roleIds) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + profileId));
        
        for (UUID roleId : roleIds) {
            if (!roleRepository.existsById(roleId)) {
                throw new EntityNotFoundException("Role not found with id: " + roleId);
            }
            
            profileRoleRepository.deleteByProfileIdAndRoleId(profileId, roleId);
        }
        
        // Invalidate authority cache for all users with this profile
        invalidateAuthorityCacheForProfileUsers(profileId);
        
        return getProfileById(profileId); // Return fresh with roles
    }
    
    @Transactional
    public ProfileDTO createIfNotExists(String name, String description) {
        if (profileRepository.existsByName(name)) {
            Profile profile = profileRepository.findByName(name)
                    .orElseThrow(() -> new EntityNotFoundException("Profile not found with name: " + name));
            
            ProfileDTO profileDTO = new ProfileDTO();
            profileDTO.setId(profile.getId());
            profileDTO.setName(profile.getName());
            profileDTO.setDescription(profile.getDescription());
            profileDTO.setCreatedAt(profile.getCreatedAt());
            profileDTO.setUpdatedAt(profile.getUpdatedAt());
            
            // Don't load roles to avoid lazy loading issues
            profileDTO.setRoles(Collections.emptyList());
            
            return profileDTO;
        } else {
            ProfileDTO profileDTO = new ProfileDTO();
            profileDTO.setName(name);
            profileDTO.setDescription(description);
            return createProfile(profileDTO);
        }
    }
    
    // Methods for user-profile assignment
    
    @Transactional
    public void assignProfileToUser(UUID profileId, UUID userId, User assignedBy) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + profileId));
        
        User user = getUserById(userId);
        
        if (userProfileRepository.existsByUserAndProfile(user, profile)) {
            throw new EntityExistsException("Profile is already assigned to this user");
        }
        
        UserProfileKey key = new UserProfileKey();
        key.setUserId(userId);
        key.setProfileId(profileId);
        
        UserProfile userProfile = new UserProfile();
        userProfile.setId(key);
        userProfile.setUser(user);
        userProfile.setProfile(profile);
        userProfile.setAssignedBy(assignedBy);
        
        userProfileRepository.save(userProfile);
        
        // Invalidate the user's authority cache
        log.info("Invalidating authority cache for user ID: {}", userId);
        authorityService.clearCacheForUser(userId);
    }
    
    @Transactional
    public void removeProfileFromUser(UUID profileId, UUID userId) {
        if (!profileRepository.existsById(profileId)) {
            throw new EntityNotFoundException("Profile not found with id: " + profileId);
        }
        
        if (!userExists(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        
        userProfileRepository.deleteByUserIdAndProfileId(userId, profileId);
        
        // Invalidate the user's authority cache
        log.info("Invalidating authority cache for user ID: {}", userId);
        authorityService.clearCacheForUser(userId);
    }
    
    // Helper methods
    
    private User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
    }
    
    private boolean userExists(UUID userId) {
        return userRepository.existsById(userId);
    }
    
    // Get users with a specific profile
    public List<UserDTO> getUsersByProfileId(UUID profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + profileId));
        
        return userProfileRepository.findByProfile(profile).stream()
                .map(userProfile -> UserDTO.fromEntitySimplified(userProfile.getUser()))
                .collect(Collectors.toList());
    }
    
    /**
     * Helper method to invalidate authority cache for all users with a specific profile
     */
    private void invalidateAuthorityCacheForProfileUsers(UUID profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found with id: " + profileId));
                
        List<UserProfile> userProfiles = userProfileRepository.findByProfile(profile);
        log.info("Invalidating authority cache for {} users with profile ID: {}", 
                userProfiles.size(), profileId);
        
        for (UserProfile userProfile : userProfiles) {
            UUID userId = userProfile.getUser().getId();
            log.info("Invalidating authority cache for user ID: {}", userId);
            authorityService.clearCacheForUser(userId);
        }
    }
} 