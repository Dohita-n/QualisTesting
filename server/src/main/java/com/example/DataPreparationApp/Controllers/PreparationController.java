package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.DTO.PreparationPreviewDTO;
import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.Preparation;
import com.example.DataPreparationApp.Model.TransformationStep;
import com.example.DataPreparationApp.Services.PreparationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/preparations")
@PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
@RequiredArgsConstructor
public class PreparationController {

    private final PreparationService preparationService;
    private static final Logger log = LoggerFactory.getLogger(PreparationController.class);

    @GetMapping
    
    public ResponseEntity<List<Preparation>> getAllPreparations(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
        
        if (isAdmin) {
            // Admins can see all preparations
            return ResponseEntity.ok(preparationService.getAllPreparations());
        } else {
            // Regular users can only see their own preparations
            return ResponseEntity.ok(preparationService.getPreparationsByCreator(authentication.getName()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Preparation> getPreparationById(@PathVariable UUID id, Authentication authentication) {

        Preparation preparation = preparationService.getPreparationById(id);
        
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
        
        
        // Check if user has permission to view this preparation
        if (!isAdmin && !preparation.getCreatedBy().getUsername().equals(authentication.getName())) {
            log.warn("Access denied for user {} to preparation created by {}", 
                    authentication.getName(), preparation.getCreatedBy().getUsername());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Force loading of transformation steps
        preparation.getTransformationSteps().size(); // This triggers the loading
        return ResponseEntity.ok(preparation);
    }

    @PostMapping
    public ResponseEntity<Preparation> createPreparation(
            @RequestBody Map<String, Object> requestBody,
            Authentication authentication) {
        
        String name = (String) requestBody.get("name");
        String description = (String) requestBody.get("description");
        UUID datasetId = UUID.fromString((String) requestBody.get("datasetId"));
        
        Preparation preparation = preparationService.createPreparation(
                name, description, datasetId, authentication.getName());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(preparation);
    }

    @PutMapping("/{id}")    
    public ResponseEntity<Preparation> updatePreparation(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> requestBody,
            Authentication authentication) {
        
        // First, check if the user has permission to update this preparation
        Preparation existingPreparation = preparationService.getPreparationById(id);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
                
        if (!isAdmin && !existingPreparation.getCreatedBy().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        String name = (String) requestBody.get("name");
        String description = (String) requestBody.get("description");
        
        Preparation preparation = preparationService.updatePreparation(id, name, description);
        
        return ResponseEntity.ok(preparation);
    }

    //Add a PUT Mapping to edit rows ,cells,columns,dataset execute names;

    @PreAuthorize("hasAuthority('ADMIN','DELETE_DATA')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePreparation(@PathVariable UUID id, Authentication authentication) {
        // First, check if the user has permission to delete this preparation
        Preparation existingPreparation = preparationService.getPreparationById(id);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
                
        if (!isAdmin && !existingPreparation.getCreatedBy().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        preparationService.deletePreparation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<Map<String, Object>> executePreparation(
            @PathVariable UUID id, 
            Authentication authentication) {
        
        // First, check if the user has permission to execute this preparation
        Preparation existingPreparation = preparationService.getPreparationById(id);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
                
        if (!isAdmin && !existingPreparation.getCreatedBy().getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        Dataset resultDataset = preparationService.executePreparation(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Preparation executed successfully");
        response.put("datasetId", resultDataset.getId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Emergency fix endpoint to repair a dataset with missing rows
     */
    @PostMapping("/fix-dataset/{datasetId}")
    public ResponseEntity<Map<String, Object>> fixDataset(@PathVariable UUID datasetId) {
        long rowCount = preparationService.fixEmptyDataset(datasetId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Dataset repair attempted");
        response.put("datasetId", datasetId);
        response.put("rowsAdded", rowCount);
        
        return ResponseEntity.ok(response);
    }
} 