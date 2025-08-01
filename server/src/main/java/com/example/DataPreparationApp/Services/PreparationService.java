package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.DTO.PreparationPreviewDTO;
import com.example.DataPreparationApp.Model.*;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.PreparationRepository;
import com.example.DataPreparationApp.Repository.TransformationStepRepository;
import com.example.DataPreparationApp.Repository.UserRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.DataPreparationApp.DTO.TransformationConfig;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreparationService {

    private final PreparationRepository preparationRepository;
    private final DatasetRepository datasetRepository;
    private final UserRepository userRepository;
    private final TransformationStepRepository transformationStepRepository;
    private final TransformationService transformationService;
    private final ObjectMapper objectMapper;
    private final DatasetRowRepository datasetRowRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    /**
     * Creates a new preparation from a dataset
     */
    public Preparation createPreparation(String name, String description, UUID datasetId, String username) {
        // Find the dataset
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new RuntimeException("Dataset not found: " + datasetId));
        
        // Find the user by username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        
        // Create the preparation entity
        Preparation preparation = Preparation.builder()
                .name(name)
                .description(description)
                .sourceDataset(dataset)
                .createdBy(user)
                .status("DRAFT")
                .build();
        
        // Save the preparation
        return preparationRepository.save(preparation);
    }
    
    /**
     * Get all preparations
     */
    public List<Preparation> getAllPreparations() {
        return preparationRepository.findAll();
    }
    
    /**
     * Get preparations created by a specific user
     */
    public List<Preparation> getPreparationsByCreator(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        
        return preparationRepository.findByCreatedById(user.getId());
    }
    
    /**
     * Get preparation by ID
     */
    public Preparation getPreparationById(UUID id) {
        return preparationRepository.findByIdWithTransformationSteps(id)
                .orElseThrow(() -> new IllegalArgumentException("Preparation not found"));
    }
    
    /**
     * Update preparation details
     */
    @Transactional
    public Preparation updatePreparation(UUID id, String name, String description) {
        Preparation preparation = getPreparationById(id);
        
        if (name != null && !name.isBlank()) {
            preparation.setName(name);
        }
        
        if (description != null) {
            preparation.setDescription(description);
        }
        
        return preparationRepository.save(preparation);
    }
    
    /**
     * Delete a preparation
     */
    @Transactional
    public void deletePreparation(UUID id) {
        Preparation preparation = getPreparationById(id);
        preparationRepository.delete(preparation);
    }
    
    /**
     * Add a transformation step to a preparation
     */
    @Transactional
    public TransformationStep addTransformationStep(UUID preparationId, String transformationType, Map<String, Object> parameters) {
        Preparation preparation = getPreparationById(preparationId);
        
        // Convert parameters map to JsonNode
        JsonNode parametersNode = objectMapper.valueToTree(parameters);
        
        // Find the highest sequence order
        int maxSequence = preparation.getTransformationSteps().stream()
                .mapToInt(TransformationStep::getSequenceOrder)
                .max()
                .orElse(0);
        
        TransformationStep step = TransformationStep.builder()
                .dataset(preparation.getSourceDataset())
                .user(preparation.getCreatedBy())
                .preparation(preparation)
                .transformationType(transformationType)
                .parameters(parametersNode)
                .appliedAt(java.time.LocalDateTime.now())
                .sequenceOrder(maxSequence + 1)
                .active(true)
                .build();
        
        return transformationStepRepository.save(step);
    }
    
    /**
     * Update a transformation step
     */
    @Transactional
    public TransformationStep updateTransformationStep(UUID preparationId, UUID stepId, Map<String, Object> changes) {
        // Verify preparation exists
        Preparation preparation = getPreparationById(preparationId);
        
        TransformationStep step = transformationStepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Transformation step not found"));
        
        // Verify step belongs to preparation
        if (!step.getPreparation().getId().equals(preparationId)) {
            throw new IllegalArgumentException("Transformation step does not belong to this preparation");
        }
        
        // Update fields based on the changes map
        if (changes.containsKey("active")) {
            step.setActive((Boolean) changes.get("active"));
        }
        
        if (changes.containsKey("parameters")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) changes.get("parameters");
            step.setParameters(objectMapper.valueToTree(parameters));
        }
        
        if (changes.containsKey("transformationType")) {
            step.setTransformationType((String) changes.get("transformationType"));
        }
        
        return transformationStepRepository.save(step);
    }
    
    /**
     * Delete a transformation step
     */
    @Transactional
    public void deleteTransformationStep(UUID preparationId, UUID stepId) {
        // Verify preparation exists
        Preparation preparation = getPreparationById(preparationId);
        
        TransformationStep step = transformationStepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Transformation step not found"));
        
        // Verify step belongs to preparation
        if (!step.getPreparation().getId().equals(preparationId)) {
            throw new IllegalArgumentException("Transformation step does not belong to this preparation");
        }
        
        // Remove the step from the preparation's collection first
        preparation.getTransformationSteps().removeIf(s -> s.getId().equals(stepId));
        
        // Delete the step from the database
        transformationStepRepository.delete(step);
        
        // Reorder remaining steps
        reorderSteps(preparation);
    }
    
    /**
     * Reorder transformation steps
     */
    @Transactional
    public List<TransformationStep> reorderTransformationSteps(UUID preparationId, List<UUID> orderedStepIds) {
        Preparation preparation = getPreparationById(preparationId);
        
        // Verify all steps belong to this preparation
        List<TransformationStep> steps = transformationStepRepository.findAllById(orderedStepIds);
        
        if (steps.size() != orderedStepIds.size()) {
            throw new IllegalArgumentException("Some transformation steps were not found");
        }
        
        for (TransformationStep step : steps) {
            if (!step.getPreparation().getId().equals(preparationId)) {
                throw new IllegalArgumentException("Some transformation steps do not belong to this preparation");
            }
        }
        
        // Create a map of step ID to sequence order for efficient updates
        Map<UUID, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < orderedStepIds.size(); i++) {
            orderMap.put(orderedStepIds.get(i), i + 1);
        }
        
        // Update sequence order based on the map
        for (TransformationStep step : steps) {
            Integer newOrder = orderMap.get(step.getId());
            if (newOrder != null) {
                step.setSequenceOrder(newOrder);
                transformationStepRepository.save(step);
            }
        }
        
        return steps.stream()
                .sorted(Comparator.comparingInt(TransformationStep::getSequenceOrder))
                .collect(Collectors.toList());
    }
    
    /**
     * Preview transformation results
     */
    @Transactional(readOnly = true)
    public PreparationPreviewDTO previewTransformation(UUID preparationId, List<TransformationStep> providedSteps, UUID datasetId) {
        Preparation preparation = getPreparationById(preparationId);
        Dataset sourceDataset = preparation.getSourceDataset();
        
        // If datasetId is not provided, use the preparation's source dataset ID
        final UUID finalDatasetId = (datasetId != null) ? datasetId : sourceDataset.getId();
        
        // Get active steps if not provided
        List<TransformationStep> activeSteps = providedSteps;
        if (activeSteps == null || activeSteps.isEmpty()) {
            activeSteps = preparation.getTransformationSteps().stream()
                    .filter(TransformationStep::getActive)
                    .sorted(Comparator.comparingInt(TransformationStep::getSequenceOrder))
                    .collect(Collectors.toList());
        }
        
        // Convert TransformationStep objects to TransformationConfig objects
        List<TransformationConfig> transformationConfigs = activeSteps.stream()
            .map(step -> {
                TransformationConfig config = new TransformationConfig();
                config.setType(step.getTransformationType());
                
                // Convert JsonNode parameters to Map
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = objectMapper.treeToValue(
                        step.getParameters(), Map.class);
                    
                    // Add dataset ID to the parameters for type checking
                    params.put("datasetId", finalDatasetId);
                    config.setParameters(params);
                    
                    // Set column info from parameters
                    if (params.containsKey("columnName")) {
                        config.setColumnName((String) params.get("columnName"));
                    }
                } catch (Exception e) {
                    log.error("Error parsing transformation parameters", e);
                }
                
                return config;
            })
            .collect(Collectors.toList());
        
        // Use TransformationService to generate preview with sample data (limited to 50 rows)
        return transformationService.generatePreview(sourceDataset, transformationConfigs, 10000);
    }
    
    /**
     * Execute preparation to create a new dataset
     */
    @Transactional
    public Dataset executePreparation(UUID preparationId) {
        Preparation preparation = getPreparationById(preparationId);
        Dataset sourceDataset = preparation.getSourceDataset();
        
        // Mark as processing
        preparation.setStatus("PROCESSING");
        preparationRepository.save(preparation);
        
        
        try {
            // Get active steps in order
            List<TransformationStep> activeSteps = preparation.getTransformationSteps().stream()
                    .filter(TransformationStep::getActive)
                    .sorted(Comparator.comparingInt(TransformationStep::getSequenceOrder))
                    .collect(Collectors.toList());
            
            if (activeSteps.isEmpty()) {
                throw new IllegalArgumentException("No active transformation steps found for preparation");
            }
            
            // Log details of active steps
            for (int i = 0; i < activeSteps.size(); i++) {
                TransformationStep step = activeSteps.get(i);
                log.info("Step {}: Type={}, Parameters={}", 
                       i+1, step.getTransformationType(), step.getParameters());
            }
            
            // Convert TransformationStep objects to TransformationConfig objects
            List<TransformationConfig> transformationConfigs = activeSteps.stream()
                .map(step -> {
                    TransformationConfig config = new TransformationConfig();
                    config.setType(step.getTransformationType());
                    
                    // Convert JsonNode parameters to Map
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> params = objectMapper.treeToValue(
                            step.getParameters(), Map.class);
                        config.setParameters(params);
                        
                        log.info("Converted parameters for step type {}: {}", 
                               step.getTransformationType(), params);
                        
                        // Set column info from parameters
                        if (params.containsKey("columnName")) {
                            config.setColumnName((String) params.get("columnName"));
                            log.info("Set columnName: {}", params.get("columnName"));
                        }
                        if (params.containsKey("columnId")) {
                            Object columnIdObj = params.get("columnId");
                            if (columnIdObj instanceof String) {
                                config.setColumnId(UUID.fromString((String) columnIdObj));
                                log.info("Set columnId from string: {}", columnIdObj);
                            } else if (columnIdObj instanceof UUID) {
                                config.setColumnId((UUID) columnIdObj);
                                log.info("Set columnId from UUID: {}", columnIdObj);
                            }
                        }
                        
                        // Special handling for the "column" parameter
                        if (params.containsKey("column")) {
                            String column = (String) params.get("column");
                            if (!params.containsKey("columnName")) {
                                config.setColumnName(column);
                                log.info("Set columnName from column parameter: {}", column);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error parsing transformation parameters: {}", e.getMessage(), e);
                    }
                    
                    return config;
                })
                .collect(Collectors.toList());
            
            log.info("Created {} transformation configs", transformationConfigs.size());
            
            // Execute all transformations on the source dataset using the batched method
            // This handles creating the target dataset and saving everything to the database
            // Use a batch size of 1000 rows for efficient processing
            log.info("Starting transformation execution with batch size 1000");
            Dataset transformedDataset = transformationService.executeTransformationsWithBatching(
                    sourceDataset, transformationConfigs, preparation.getCreatedBy(), true, 1000);
            
            log.info("Transformation completed, dataset created: {}", transformedDataset.getId());
            
            // Verify row count
            long rowCount = datasetRowRepository.countByDatasetId(transformedDataset.getId());
            log.info("Verified row count in dataset_rows table: {} rows for dataset {}", 
                    rowCount, transformedDataset.getId());
            
            if (rowCount == 0) {
                log.warn("No rows were saved to the dataset_rows table for dataset {}!", 
                        transformedDataset.getId());
            }
            
            // Update preparation with the new dataset
            preparation.setOutputDataset(transformedDataset);
            preparation.setStatus("EXECUTED");
            preparationRepository.save(preparation);
            
            
            return transformedDataset;
            
        } catch (Exception e) {
            log.error("Error executing preparation: {}", e.getMessage(), e);
            preparation.setStatus("ERROR");
            preparationRepository.save(preparation);
            log.info("Preparation status updated to ERROR due to exception");
            log.info("======= PREPARATION EXECUTION FAILED ========");
            throw new RuntimeException("Failed to execute preparation: " + e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to reorder steps after deletion
     */
    private void reorderSteps(Preparation preparation) {
        // Refresh the preparation entity to make sure we have the latest state
        preparation = preparationRepository.save(preparation);
        
        List<TransformationStep> steps = preparation.getTransformationSteps().stream()
                .filter(step -> step.getId() != null) // Filter out any steps without IDs
                .sorted(Comparator.comparingInt(TransformationStep::getSequenceOrder))
                .collect(Collectors.toList());
        
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setSequenceOrder(i + 1);
            transformationStepRepository.save(steps.get(i));
        }
    }

    /**
     * Emergency fix to repair a dataset with missing rows
     * This method will find the preparation that created this dataset and re-execute the transformations
     * directly writing to the existing dataset instead of creating a new one
     * 
     * @param datasetId The ID of the dataset to repair
     * @return The number of rows added to the dataset
     */
    @Transactional
    public long fixEmptyDataset(UUID datasetId) {
        log.info("Starting emergency dataset repair for dataset ID: {}", datasetId);
        
        // Find the dataset
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));
        
        // Check if it's already populated
        long initialRowCount = 0;
        try {
            initialRowCount = datasetRowRepository.countByDatasetId(datasetId);
            log.info("Dataset currently has {} rows", initialRowCount);
            
            if (initialRowCount > 0) {
                log.info("Dataset already has {} rows, no repair needed", initialRowCount);
                return initialRowCount;
            }
        } catch (Exception e) {
            log.error("Error checking row count: {}", e.getMessage(), e);
        }
        
        // Find the preparation that created this dataset
        Preparation preparation = null;
        try {
            List<Preparation> matchingPreps = preparationRepository.findAll().stream()
                    .filter(p -> p.getOutputDataset() != null && p.getOutputDataset().getId().equals(datasetId))
                    .collect(Collectors.toList());
            
            if (!matchingPreps.isEmpty()) {
                preparation = matchingPreps.get(0);
                log.info("Found preparation that created this dataset: {}", preparation.getId());
            } else {
                log.error("No preparation found that created dataset {}", datasetId);
                throw new IllegalArgumentException("No preparation found for this dataset");
            }
        } catch (Exception e) {
            log.error("Error finding preparation: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to find preparation that created this dataset", e);
        }
        
        // Get the source dataset from the preparation
        Dataset sourceDataset = preparation.getSourceDataset();
        log.info("Source dataset: {}", sourceDataset.getId());
        
        // Get active transformation steps
        List<TransformationStep> activeSteps = preparation.getTransformationSteps().stream()
                .filter(TransformationStep::getActive)
                .sorted(Comparator.comparingInt(TransformationStep::getSequenceOrder))
                .collect(Collectors.toList());
        
        log.info("Found {} active transformation steps", activeSteps.size());
        
        // Convert steps to transformation configs
        List<TransformationConfig> transformationConfigs = activeSteps.stream()
            .map(step -> {
                TransformationConfig config = new TransformationConfig();
                config.setType(step.getTransformationType());
                
                // Convert JsonNode parameters to Map
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = objectMapper.treeToValue(
                        step.getParameters(), Map.class);
                    config.setParameters(params);
                    
                    // Set column info from parameters
                    if (params.containsKey("columnName")) {
                        config.setColumnName((String) params.get("columnName"));
                    }
                    if (params.containsKey("columnId")) {
                        Object columnIdObj = params.get("columnId");
                        if (columnIdObj instanceof String) {
                            config.setColumnId(UUID.fromString((String) columnIdObj));
                        } else if (columnIdObj instanceof UUID) {
                            config.setColumnId((UUID) columnIdObj);
                        }
                    }
                    
                    // Special handling for the "column" parameter
                    if (params.containsKey("column")) {
                        String column = (String) params.get("column");
                        if (!params.containsKey("columnName")) {
                            config.setColumnName(column);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error parsing transformation parameters: {}", e.getMessage(), e);
                }
                
                return config;
            })
            .collect(Collectors.toList());
        
        log.info("Starting direct transformation to repair dataset");
        
        // Execute a special transformation directly into the target dataset
        long addedRows = executeDirectRepair(sourceDataset, dataset, transformationConfigs, preparation.getCreatedBy());
        
        log.info("Repair completed. Added {} rows to dataset {}", addedRows, datasetId);
        
        return addedRows;
    }

    /**
     * Execute a direct repair by transforming data from source to target dataset
     * without creating a new dataset
     */
    private long executeDirectRepair(Dataset sourceDataset, Dataset targetDataset, 
                                   List<TransformationConfig> transformations, User user) {
        log.info("Executing direct repair from dataset {} to dataset {}", 
                 sourceDataset.getId(), targetDataset.getId());
        
        // Get source rows
        List<DatasetRow> sourceRows;
        try {
            // Try direct repo method first
            sourceRows = datasetRowRepository.findByDatasetIdOrderByRowNumber(sourceDataset.getId());
            log.info("Retrieved {} rows from source dataset", sourceRows.size());
            
            if (sourceRows.isEmpty()) {
                // Try native query as fallback
                log.warn("No rows found with standard query, trying native query");
                sourceRows = datasetRowRepository.findByDatasetIdNative(sourceDataset.getId(), 10000);
                log.info("Retrieved {} rows using native query", sourceRows.size());
            }
        } catch (Exception e) {
            log.error("Error retrieving source rows: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve source data for repair", e);
        }
        
        if (sourceRows.isEmpty()) {
            log.error("No rows found in source dataset - cannot repair target");
            return 0;
        }
        
        // Get source columns and create column maps
        List<DatasetColumn> sourceColumns = datasetColumnRepository.findByDatasetIdOrderByPosition(sourceDataset.getId());
        Map<String, Integer> columnPositions = new HashMap<>();
        for (int i = 0; i < sourceColumns.size(); i++) {
            columnPositions.put(sourceColumns.get(i).getName(), i);
        }
        
        // Apply transformations to source rows
        List<DatasetRow> transformedRows = transformationService.applyTransformations(sourceRows, sourceColumns, 
                                                                                    columnPositions, transformations);
        log.info("After applying transformations: {} rows", transformedRows.size());
        
        // Save rows directly to target dataset
        if (!transformedRows.isEmpty()) {
            List<DatasetRow> newRows = new ArrayList<>();
            long savedCount = 0;
            
            for (int i = 0; i < transformedRows.size(); i++) {
                DatasetRow originalRow = transformedRows.get(i);
                
                try {
                    // Validate JSON
                    String jsonStr = objectMapper.writeValueAsString(originalRow.getData());
                    JsonNode validatedData = objectMapper.readTree(jsonStr);
                    
                    DatasetRow newRow = DatasetRow.builder()
                            .dataset(targetDataset)
                            .rowNumber(i)
                            .data(validatedData)
                            .build();
                    newRows.add(newRow);
                    
                    // Save in batches of 500
                    if (newRows.size() >= 500 || i == transformedRows.size() - 1) {
                        log.debug("Saving batch of {} rows", newRows.size());
                        List<DatasetRow> saved = datasetRowRepository.saveAll(newRows);
                        savedCount += saved.size();
                        newRows.clear();
                    }
                } catch (Exception e) {
                    log.error("Error processing row {}: {}", i, e.getMessage());
                }
            }
            
            // Update dataset metadata
            targetDataset.setRowCount(savedCount);
            datasetRepository.save(targetDataset);
            
            return savedCount;
        }
        
        return 0;
    }
} 