package com.example.DataPreparationApp.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for returning preview data from a preparation's transformations
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PreparationPreviewDTO {
    private List<String> headers;
    private List<Map<String, Object>> data;
} 