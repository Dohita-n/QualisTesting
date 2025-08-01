package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Services.AuthorizationService;
import com.example.DataPreparationApp.Services.DataProfilingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiling")
@PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DataProfilingController extends BaseController {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DataProfilingService dataProfilingService;

    public DataProfilingController(
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            DataProfilingService dataProfilingService,
            AuthorizationService authorizationService) {
        super(authorizationService);
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.dataProfilingService = dataProfilingService;
    }

    @GetMapping("/datasets/{datasetId}/summary")
    public ResponseEntity<Map<String, Object>> getDatasetSummary(
            @PathVariable UUID datasetId,
            @RequestParam UUID userId) throws Exception {
        Map<String, Object> summary = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + datasetId));
            
            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);
            
            return dataProfilingService.generateDatasetSummary(dataset);
        });
        
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/datasets/{datasetId}/columns/{columnId}/statistics")
    public ResponseEntity<Map<String, Object>> getColumnStatistics(
            @PathVariable UUID datasetId,
            @PathVariable UUID columnId,
            @RequestParam UUID userId) throws Exception {
        Map<String, Object> statistics = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + datasetId));
            
            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);
            
            DatasetColumn column = datasetColumnRepository.findById(columnId)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found with id: " + columnId));
            
            if (!column.getDataset().getId().equals(dataset.getId())) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }
            
            return dataProfilingService.generateColumnStatistics(column);
        });
        
        return ResponseEntity.ok(statistics);
    }
    
    @PostMapping("/datasets/{datasetId}/analyze")
    public ResponseEntity<Map<String, String>> analyzeDataset(
            @PathVariable UUID datasetId,
            @RequestParam UUID userId) throws Exception {
        Map<String, String> result = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(datasetId)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + datasetId));
            
            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);
            
            List<DatasetColumn> columns = datasetColumnRepository.findByDataset(dataset);
            dataProfilingService.analyzeDataset(dataset, columns);
            
            return Map.of("message", "Dataset analysis started");
        });
        
        return ResponseEntity.ok(result);
    }
} 