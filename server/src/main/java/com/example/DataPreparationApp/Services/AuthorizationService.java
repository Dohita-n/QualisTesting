package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.Profile;
import com.example.DataPreparationApp.Model.ProfileRole;
import com.example.DataPreparationApp.Model.Role;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Model.UserProfile;
import com.example.DataPreparationApp.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizationService {
    
    private final UserRepository userRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private static final String VIEW_ALL_DATASETS_ROLE = "VIEW_ALL_DATASETS";
    private static final String EDIT_DATA_ROLE = "EDIT_DATA";
    private static final String ADMIN_ROLE = "ADMIN";
    
    /**
     * Checks if a user has permission to view a specific dataset
     * @param requestingUserId The ID of the user trying to access the dataset
     * @param datasetOwnerId The ID of the user who owns the dataset
     * @return true if the user has permission, false otherwise
     */
    public boolean canViewDataset(UUID requestingUserId, UUID datasetOwnerId) {
        // Users can always view their own datasets
        if (requestingUserId.equals(datasetOwnerId)) {
            return true;
        }
        
        // Otherwise, check if the user has the VIEW_ALL_DATASETS role
        return hasViewAllDatasetsPermission(requestingUserId);
    }
    
    /**
     * Checks if a user has permission to view all datasets
     * @param userId The ID of the user
     * @return true if the user has the required permission, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasViewAllDatasetsPermission(UUID userId) {
        return hasRole(userId, VIEW_ALL_DATASETS_ROLE);
    }
    
    /**
     * Checks if a user has permission to edit data
     * @param userId The ID of the user
     * @return true if the user has the required permission, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasEditDataPermission(UUID userId) {
        return hasRole(userId, EDIT_DATA_ROLE);
    }
    
    /**
     * Checks if a user has admin privileges
     * @param userId The ID of the user
     * @return true if the user is an admin, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isAdmin(UUID userId) {
        return hasRole(userId, ADMIN_ROLE);
    }
    
    /**
     * Safely checks if a user has a specific role using direct JPQL queries
     * to avoid lazy loading issues and ConcurrentModificationExceptions
     * 
     * @param userId The user ID to check
     * @param roleName The role name to check for
     * @return true if the user has the role, false otherwise
     */
    @Transactional(readOnly = true)
    private boolean hasRole(UUID userId, String roleName) {
        if (userId == null || roleName == null) {
            return false;
        }
        
        // Use a direct query that joins all necessary tables to check for role existence
        // This avoids lazy loading and collection iteration issues
        Long count = entityManager.createQuery(
            "SELECT COUNT(r) FROM User u " +
            "JOIN u.userProfiles up " +
            "JOIN up.profile p " +
            "JOIN p.profileRoles pr " +
            "JOIN pr.role r " +
            "WHERE u.id = :userId AND r.name = :roleName", Long.class)
            .setParameter("userId", userId)
            .setParameter("roleName", roleName)
            .getSingleResult();
            
        return count > 0;
    }
} 