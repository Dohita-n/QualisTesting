package com.example.DataPreparationApp.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Configuration for a transformation to be applied
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationConfig {
    /**
     * Type of transformation
     */
    private String type;
    
    /**
     * ID of the column to transform (may be null for row-based transformations)
     */
    private UUID columnId;
    
    /**
     * Name of the column (alternative to columnId)
     */
    private String columnName;
    
    /**
     * Parameters specific to the transformation type
     */
    private Map<String, Object> parameters;
} 