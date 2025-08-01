package com.example.DataPreparationApp.Config;

import com.example.DataPreparationApp.DTO.ProfileDTO;
import com.example.DataPreparationApp.DTO.RoleDTO;
import com.example.DataPreparationApp.Model.Profile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.example.DataPreparationApp.Model.ProfileRoleKey;
import com.example.DataPreparationApp.Model.Role;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Model.UserProfile;
import com.example.DataPreparationApp.Model.UserProfileKey;
import com.example.DataPreparationApp.Repository.ProfileRepository;
import com.example.DataPreparationApp.Repository.ProfileRoleRepository;
import com.example.DataPreparationApp.Repository.RoleRepository;
import com.example.DataPreparationApp.Repository.UserProfileRepository;
import com.example.DataPreparationApp.Repository.UserRepository;
import com.example.DataPreparationApp.Services.ProfileService;
import com.example.DataPreparationApp.Services.RoleService;
import com.example.DataPreparationApp.Services.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@DependsOn("userService")  // This ensures UserService's @PostConstruct runs before this class's @PostConstruct
public class DataInitializer {
    
    private final RoleService roleService;
    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RoleRepository roleRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserService userService; // Added UserService
    
    
    public DataInitializer(
            RoleService roleService,
            ProfileService profileService,
            UserRepository userRepository,
            ProfileRepository profileRepository,
            RoleRepository roleRepository,
            ProfileRoleRepository profileRoleRepository,
            UserProfileRepository userProfileRepository,
            UserService userService) { // Added UserService parameter
        this.roleService = roleService;
        this.profileService = profileService;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.roleRepository = roleRepository;
        this.profileRoleRepository = profileRoleRepository;
        this.userProfileRepository = userProfileRepository;
        this.userService = userService; // Initialize UserService
    }
    
    @PostConstruct
    @Transactional
    public void initialize() {
            
            // Create basic application roles
            RoleDTO uploadRole = createRoleIfNotExists("UPLOAD_DATA", "Can upload data files");
            RoleDTO deleteRole = createRoleIfNotExists("DELETE_DATA","Can delete datasets and preparations");
            RoleDTO viewRole = createRoleIfNotExists("VIEW_DATA", "Can view datasets, pattern validations and access data profiler for statistical insights");
            RoleDTO exportRole = createRoleIfNotExists("EXPORT_DATA", "Can export data to CSV");
            RoleDTO editRole = createRoleIfNotExists("EDIT_DATA", "Has access to data cleaning, change the pattern validation and data Type for each column");
            RoleDTO adminRole = createRoleIfNotExists("ADMIN", "Full administrative access");
            //RoleDTO pipelineRole = createRoleIfNotExists("MANAGE_PIPELINES", "Can create and manage data pipelines");
            //RoleDTO filterRole = createRoleIfNotExists("FILTER_DATA", "Can apply filters to data");
            
            // Create default profiles
            ProfileDTO viewerProfile = createProfileIfNotExists("VIEWER", "Basic view-only access");
            ProfileDTO editorProfile = createProfileIfNotExists("EDITOR", "Can view and edit data");
            //ProfileDTO dataManagerProfile = createProfileIfNotExists("DATA_MANAGER", "Can manage all data operations");
            ProfileDTO adminProfile = createProfileIfNotExists("ADMIN", "Administrator with full access");
            
            // Assign roles to profiles - with null checking
            if (viewerProfile != null && viewerProfile.getId() != null && viewRole != null && viewRole.getId() != null) {
                assignRolesToProfile(viewerProfile.getId(), Collections.singletonList(viewRole.getId()));
            }

            if (adminProfile !=null && adminProfile.getId() != null && adminRole != null && adminRole.getId() != null) {
                assignRolesToProfile(adminProfile.getId(), Collections.singletonList(adminRole.getId()));
            }

            if (editorProfile != null && editorProfile.getId() != null) {
                List<UUID> editorRoles = new ArrayList<>();
                if (viewRole != null && viewRole.getId() != null) editorRoles.add(viewRole.getId());
                //if (filterRole != null && filterRole.getId() != null) editorRoles.add(filterRole.getId());
                if (editRole != null && editRole.getId() != null) editorRoles.add(editRole.getId());
                if (exportRole != null && exportRole.getId() != null) editorRoles.add(exportRole.getId());
                if (uploadRole != null && uploadRole.getId() != null) editorRoles.add(uploadRole.getId());
                if (deleteRole != null && deleteRole.getId() != null) editorRoles.add(deleteRole.getId());
                if (!editorRoles.isEmpty()) {
                    assignRolesToProfile(editorProfile.getId(), editorRoles);
                }
            }
            
           /*  if (dataManagerProfile != null && dataManagerProfile.getId() != null) {
                List<UUID> dataManagerRoles = new ArrayList<>();
                if (viewRole != null && viewRole.getId() != null) dataManagerRoles.add(viewRole.getId());
                if (uploadRole != null && uploadRole.getId() != null) dataManagerRoles.add(uploadRole.getId());
                //if (filterRole != null && filterRole.getId() != null) dataManagerRoles.add(filterRole.getId());
                if (editRole != null && editRole.getId() != null) dataManagerRoles.add(editRole.getId());
                if (exportRole != null && exportRole.getId() != null) dataManagerRoles.add(exportRole.getId());
                //if (pipelineRole != null && pipelineRole.getId() != null) dataManagerRoles.add(pipelineRole.getId());
                
                if (!dataManagerRoles.isEmpty()) {
                    assignRolesToProfile(dataManagerProfile.getId(), dataManagerRoles);
                }
            } */
            
            // Assign admin profile to default admin user (if exists)
            if (adminProfile != null && adminProfile.getId() != null) {
                assignAdminProfileToAdminUser(adminProfile.getId());
            }
    }
    
    private RoleDTO createRoleIfNotExists(String name, String description) {
        try {
            return roleService.getRoleByName(name);
        } catch (Exception e) {
            log.info("Creating role: {}", name);
            RoleDTO roleDTO = new RoleDTO();
            roleDTO.setName(name);
            roleDTO.setDescription(description);
            try {
                return roleService.createRole(roleDTO);
            } catch (Exception ex) {
                log.error("Error creating role: {}", name, ex);
                return null;
            }
        }
    }
    
    private ProfileDTO createProfileIfNotExists(String name, String description) {
        try {
            return profileService.createIfNotExists(name, description);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void assignRolesToProfile(UUID profileId, List<UUID> roleIds) {
        if (profileId == null || roleIds == null || roleIds.isEmpty()) {
            log.warn("Invalid input for assignRolesToProfile. ProfileId: {}, RoleIds: {}", profileId, roleIds);
            return;
        }
        
        try {
            // Check if the profile exists
            Profile profile = profileRepository.findById(profileId)
                    .orElseThrow(() -> new RuntimeException("Profile not found: " + profileId));
            
            // Check if roles exist and manually create the associations
            for (UUID roleId : roleIds) {
                try {
                    Role role = roleRepository.findById(roleId)
                            .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
                    
                    // Check if association already exists
                    ProfileRoleKey key = new ProfileRoleKey();
                    key.setProfileId(profileId);
                    key.setRoleId(roleId);
                    
                    if (!profileRoleRepository.existsById(key)) {
                        ProfileRole profileRole = new ProfileRole();
                        profileRole.setId(key);
                        profileRole.setProfile(profile);
                        profileRole.setRole(role);
                        profileRoleRepository.save(profileRole);
                        
                        log.info("Assigned role {} to profile {}", role.getName(), profile.getName());
                    }
                } catch (Exception e) {
                    log.error("Error assigning role {} to profile {}: {}", roleId, profileId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error assigning roles to profile: {}", e.getMessage());
        }
    }
    
    private void assignAdminProfileToAdminUser(UUID adminProfileId) {
        try {
            // Make sure the admin user exists - will be created by UserService thanks to @DependsOn
            Optional<User> adminUserOpt = userRepository.findByUsername("admin"); // Using simpler find method
            
            if (adminUserOpt.isPresent()) {
                User adminUser = adminUserOpt.get();
                Profile adminProfile = profileRepository.findById(adminProfileId)
                        .orElseThrow(() -> new RuntimeException("Admin profile not found"));
                
                // Check if already assigned
                if (userProfileRepository.existsByUserAndProfile(adminUser, adminProfile)) {
                    log.info("Admin profile already assigned to admin user");
                    return;
                }
                
                // Assign admin profile to admin user
                try {
                    log.info("Assigning admin profile to admin user");
                    
                    // Create a detached UserProfile entity to avoid modifying collections
                    UserProfileKey key = new UserProfileKey();
                    key.setUserId(adminUser.getId());
                    key.setProfileId(adminProfileId);
                    
                    UserProfile userProfile = new UserProfile();
                    userProfile.setId(key);
                    userProfile.setUser(adminUser);
                    userProfile.setProfile(adminProfile);
                    userProfile.setAssignedBy(adminUser); // Self-assigned
                    
                    // Save the user profile directly without modifying collections
                    userProfileRepository.save(userProfile);
                    
                    log.info("Admin profile assigned successfully");
                } catch (Exception e) {
                    log.error("Error assigning admin profile to admin user: {}", e.getMessage(), e);
                }
            } else {
                log.warn("Default admin user not found - skipping profile assignment");
            }
        } catch (Exception e) {
            log.error("Error in assignAdminProfileToAdminUser", e);
        }
    }
} 