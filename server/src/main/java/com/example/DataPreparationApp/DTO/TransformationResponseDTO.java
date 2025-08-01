package com.example.DataPreparationApp.DTO;

import com.example.DataPreparationApp.Model.TransformationStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransformationResponseDTO {
    
    private UUID transformationId;
    private String transformationType;
    private int changesApplied;
    private UUID datasetId;
    private String status;
    
    // Static factory method to create from a TransformationStep
    public static TransformationResponseDTO fromTransformationStep(TransformationStep step, int changesApplied) {
        return TransformationResponseDTO.builder()
                .transformationId(step.getId())
                .transformationType(step.getTransformationType())
                .changesApplied(changesApplied)
                .datasetId(step.getDataset().getId())
                .status("SUCCESS")
                .build();
    }
} 