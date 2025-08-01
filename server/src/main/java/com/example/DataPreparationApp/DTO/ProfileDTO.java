package com.example.DataPreparationApp.DTO;

import com.example.DataPreparationApp.Model.Profile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileDTO {
    private UUID id;
    private String name;
    private String description;
    private List<RoleDTO> roles;
    private List<String> roleNames;
    private List<UUID> roleIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ProfileDTO fromEntity(Profile profile) {
        List<RoleDTO> rolesList = Collections.emptyList();
        
        try {
            if (profile.getProfileRoles() != null) {
                // Create a defensive copy to avoid ConcurrentModificationException
                Set<ProfileRole> profileRolesCopy = new HashSet<>();
                synchronized (profile.getProfileRoles()) {
                    profileRolesCopy.addAll(profile.getProfileRoles());
                }
                
                rolesList = new ArrayList<>();
                for (ProfileRole profileRole : profileRolesCopy) {
                    if (profileRole != null && profileRole.getRole() != null) {
                        rolesList.add(RoleDTO.fromEntity(profileRole.getRole()));
                    }
                }
            }
        } catch (Exception e) {
            // If there's any exception during role processing, return empty roles
            rolesList = Collections.emptyList();
        }
        
        return ProfileDTO.builder()
                .id(profile.getId())
                .name(profile.getName())
                .description(profile.getDescription())
                .roles(rolesList)
                .roleNames(rolesList.stream().map(RoleDTO::getName).collect(Collectors.toList()))
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
    
    /**
     * Create a simplified DTO with just basic profile information, without roles
     * This avoids lazy loading issues
     */
    public static ProfileDTO fromEntitySimplified(Profile profile) {
        return ProfileDTO.builder()
                .id(profile.getId())
                .name(profile.getName())
                .description(profile.getDescription())
                .roles(Collections.emptyList())
                .roleNames(Collections.emptyList())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
    
    public Profile toEntity() {
        return Profile.builder()
                .id(this.id)
                .name(this.name)
                .description(this.description)
                .build();
    }
} 