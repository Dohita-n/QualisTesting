package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Services.AuthorizationService;
import com.example.DataPreparationApp.Services.ValidationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/validation")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ValidationController extends BaseController {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final ValidationService validationService;

    public ValidationController(
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            ValidationService validationService,
            AuthorizationService authorizationService) {
        super(authorizationService);
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.validationService = validationService;
    }

    @GetMapping("/datasets/{datasetId}/columns/{columnId}")
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getColumnValidationInfo(
            @PathVariable UUID datasetId,
            @PathVariable UUID columnId,
            @RequestParam UUID userId) throws Exception {

        Map<String, Object> result = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + datasetId));
            
            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);
            
            DatasetColumn column = datasetColumnRepository.findById(columnId)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found with id: " + columnId));
            
            if (!column.getDataset().getId().equals(dataset.getId())) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }
            
            return validationService.getColumnValidationInfo(column);
        });
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/datasets/{datasetId}/columns/{columnId}/validate")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> validateColumn(
            @PathVariable UUID datasetId,
            @PathVariable UUID columnId,
            @RequestParam UUID userId,
            @RequestParam String pattern) throws Exception {
        
        Map<String, Object> result = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + datasetId));
            
            // Check if the user has permission to edit this dataset
            checkDatasetAccess(userId, dataset);
            
            DatasetColumn column = datasetColumnRepository.findById(columnId)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found with id: " + columnId));
            
            if (!column.getDataset().getId().equals(dataset.getId())) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }
            
            return validationService.validateColumn(column, pattern);
        });
        
        return ResponseEntity.ok(result);
    }

    /**
     * Fix duplicate statistics for all columns in a dataset
     * This doesn't require admin privileges
     */
    @PostMapping("/datasets/{datasetId}/fix-duplicates")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'VIEW_DATA', 'ADMIN')")
    public ResponseEntity<?> fixDuplicatesForDataset(
            @PathVariable UUID datasetId,
            @RequestParam String userId) {
            
        try {
            UUID userUuid = UUID.fromString(userId);
            
            // Find the dataset
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));
            
            // Check authorization
            if (!authorizationService.canViewDataset(userUuid, dataset.getFile().getUser().getId())) {
                return ResponseEntity.status(403).body("User not authorized to access this dataset");
            }
            
            // Get all columns for this dataset
            List<DatasetColumn> columns = datasetColumnRepository.findByDataset(dataset);
            
            // Fix duplicates for each column
            Map<String, Object> results = new HashMap<>();
            List<Map<String, Object>> fixResults = columns.stream()
                .map(column -> {
                    Map<String, Object> columnResult = new HashMap<>();
                    columnResult.put("columnId", column.getId().toString());
                    columnResult.put("columnName", column.getName());
                    
                    try {
                        validationService.fixDuplicatesWithSql(column.getId());
                        columnResult.put("fixed", true);
                    } catch (Exception e) {
                        columnResult.put("fixed", false);
                        columnResult.put("error", e.getMessage());
                    }
                    
                    return columnResult;
                })
                .collect(Collectors.toList());
            
            results.put("fixedColumns", fixResults);
            results.put("totalColumns", columns.size());
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
} 