package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Services.AuthorizationService;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Base controller with common methods for authorization
 */
public abstract class BaseController {
    
    protected final AuthorizationService authorizationService;
    
    protected BaseController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }
    
    /**
     * Check if a user has permission to access a dataset
     * @param userId ID of the requesting user
     * @param dataset The dataset to check access for
     * @throws SecurityException if access is denied
     */
    protected void checkDatasetAccess(UUID userId, Dataset dataset) {
        UUID datasetOwnerId = dataset.getFile().getUser().getId();
        if (!authorizationService.canViewDataset(userId, datasetOwnerId)) {
            throw new SecurityException("You do not have permission to view this dataset");
        }
    }
    
    /**
     * Execute an operation that may throw exceptions
     * @param operation The operation to execute
     * @return Result of the operation
     * @throws Exception if operation fails
     */
    protected <T> T executeOperation(Supplier<T> operation) throws Exception {
        return operation.get();
    }
} 