package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.Profile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.example.DataPreparationApp.Model.Role;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Model.UserProfile;
import com.example.DataPreparationApp.Repository.UserProfileRepository;
import com.example.DataPreparationApp.Repository.ProfileRoleRepository;
import com.example.DataPreparationApp.Repository.RoleRepository;
import com.example.DataPreparationApp.Repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service dedicated to handling user authorities safely,
 * separated from the User entity to avoid lazy loading issues.
 */
@Service
@Slf4j
public class AuthorityService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileRoleRepository profileRoleRepository;
    private final RoleRepository roleRepository;
    
    // Cache for authorities to avoid frequent DB queries
    private final ConcurrentHashMap<UUID, Set<GrantedAuthority>> authoritiesCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AuthorityService(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            ProfileRoleRepository profileRoleRepository,
            RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.profileRoleRepository = profileRoleRepository;
        this.roleRepository = roleRepository;
    }
    
    /**
     * Get authorities for a user safely by directly querying the database
     */
    @Transactional
    public Collection<? extends GrantedAuthority> getAuthorities(User user) {
        if (user == null || user.getId() == null) {
            return new HashSet<>();
        }
        
        // Special case for admin user - hardcoded authorities
        if ("admin".equals(user.getUsername())) {
            Set<GrantedAuthority> adminAuthorities = new HashSet<>();
            adminAuthorities.add(new SimpleGrantedAuthority("ADMIN"));
            //adminAuthorities.add(new SimpleGrantedAuthority("VIEW_DATA"));
            //adminAuthorities.add(new SimpleGrantedAuthority("UPLOAD_DATA"));
            //adminAuthorities.add(new SimpleGrantedAuthority("FILTER_DATA"));
            //adminAuthorities.add(new SimpleGrantedAuthority("EXPORT_DATA"));
            //adminAuthorities.add(new SimpleGrantedAuthority("EDIT_DATA"));
            //adminAuthorities.add(new SimpleGrantedAuthority("MANAGE_PIPELINES"));
            return adminAuthorities;
        }
        
        // Use cached authorities if available
        if (authoritiesCache.containsKey(user.getId())) {
            return authoritiesCache.get(user.getId());
        }
        
        try {
            Set<GrantedAuthority> authorities = new HashSet<>();
            
            // Get all user profiles directly with a dedicated query
            List<UserProfile> userProfiles = userProfileRepository.findByUserId(user.getId());
            
            for (UserProfile userProfile : userProfiles) {
                if (userProfile != null && userProfile.getProfile() != null) {
                    UUID profileId = userProfile.getProfile().getId();
                    
                    // Get roles for this profile with a dedicated query
                    List<ProfileRole> profileRoles = profileRoleRepository.findByProfileId(profileId);
                    
                    for (ProfileRole profileRole : profileRoles) {
                        if (profileRole != null && profileRole.getRole() != null) {
                            Role role = profileRole.getRole();
                            if (role.getName() != null) {
                                authorities.add(new SimpleGrantedAuthority(role.getName()));
                            }
                        }
                    }
                }
            }
            
            // Cache the authorities
            authoritiesCache.put(user.getId(), authorities);
            
            return authorities;
        } catch (Exception e) {
            log.error("Error loading authorities for user {}: {}", user.getUsername(), e.getMessage(), e);
            return new HashSet<>();
        }
    }
    
    /**
     * For backward compatibility with existing code
     */
    @Transactional
    public Collection<? extends GrantedAuthority> getAuthoritiesForUser(User user) {
        return getAuthorities(user);
    }
    
    /**
     * Clear the authorities cache for a user when their profiles change
     */
    public void clearCacheForUser(UUID userId) {
        if (userId != null) {
            authoritiesCache.remove(userId);
        }
    }
    
    /**
     * Clear the entire authorities cache
     */
    public void clearCache() {
        authoritiesCache.clear();
    }
} 