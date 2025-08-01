package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.DTO.ColumnTypeChangeDTO;
import com.example.DataPreparationApp.DTO.PreparationPreviewDTO;
import com.example.DataPreparationApp.DTO.TransformationResponseDTO;
import com.example.DataPreparationApp.Model.Preparation;
import com.example.DataPreparationApp.Model.TransformationStep;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Services.PreparationService;
import com.example.DataPreparationApp.Services.TransformationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
@RequiredArgsConstructor
public class TransformationController {
    
    private final TransformationService transformationService;
    private final PreparationService preparationService;
    
    /**
     * Apply column data type changes to a dataset.
     */
    @PostMapping("/api/transformations/data-types/{datasetId}")
    public ResponseEntity<TransformationResponseDTO> applyDataTypeChanges(
            @PathVariable UUID datasetId,
            @RequestBody List<ColumnTypeChangeDTO> changes,
            Authentication authentication) {
        
        User currentUser = (User) authentication.getPrincipal();
        TransformationResponseDTO response = transformationService.applyColumnDataTypeChanges(
                datasetId, changes, currentUser);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Add a transformation step to a preparation
     */
    @PostMapping("/api/preparations/{preparationId}/transformations")
    public ResponseEntity<TransformationStep> addTransformationStep(
            @PathVariable UUID preparationId,
            @RequestBody Map<String, Object> requestBody) {
        
        String transformationType = (String) requestBody.get("transformationType");
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) requestBody.get("parameters");
        
        TransformationStep step = preparationService.addTransformationStep(preparationId, transformationType, parameters);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(step);
    }

    /**
     * Update a transformation step
     */
    @PutMapping("/api/preparations/{preparationId}/transformations/{stepId}")
    public ResponseEntity<TransformationStep> updateTransformationStep(
            @PathVariable UUID preparationId,
            @PathVariable UUID stepId,
            @RequestBody Map<String, Object> changes) {
        
        TransformationStep step = preparationService.updateTransformationStep(preparationId, stepId, changes);
        
        return ResponseEntity.ok(step);
    }

    /**
     * Delete a transformation step
     */
    @DeleteMapping("/api/preparations/{preparationId}/transformations/{stepId}")
    public ResponseEntity<Void> deleteTransformationStep(
            @PathVariable UUID preparationId,
            @PathVariable UUID stepId) {
        
        preparationService.deleteTransformationStep(preparationId, stepId);
        
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorder transformation steps
     */
    @PutMapping("/api/preparations/{preparationId}/transformations/reorder")
    public ResponseEntity<List<TransformationStep>> reorderTransformationSteps(
            @PathVariable UUID preparationId,
            @RequestBody Map<String, Object> requestBody) {
        
        @SuppressWarnings("unchecked")
        List<String> orderedStepIds = (List<String>) requestBody.get("stepIds");
        
        List<UUID> stepUuids = orderedStepIds.stream()
                .map(UUID::fromString)
                .toList();
        
        List<TransformationStep> steps = preparationService.reorderTransformationSteps(preparationId, stepUuids);
        
        return ResponseEntity.ok(steps);
    }
    
    /**
     * Preview a transformation without persisting changes
     */
    @PostMapping("/api/preparations/{preparationId}/preview")
    public ResponseEntity<PreparationPreviewDTO> previewTransformation(
            @PathVariable UUID preparationId,
            @RequestParam(required = false) UUID stepId) {
        
        // Get the preparation first to obtain the dataset ID
        Preparation preparation = preparationService.getPreparationById(preparationId);
        UUID datasetId = preparation.getSourceDataset().getId();
        
        // Pass the step ID and dataset ID
        PreparationPreviewDTO preview = preparationService.previewTransformation(preparationId, null, datasetId);
        return ResponseEntity.ok(preview);
    }
} 