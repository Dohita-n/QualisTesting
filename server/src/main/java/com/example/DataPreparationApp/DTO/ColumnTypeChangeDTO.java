package com.example.DataPreparationApp.DTO;

import com.example.DataPreparationApp.Model.DatasetColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnTypeChangeDTO {
    
    private UUID columnId;
    private DatasetColumn.DataType newDataType;
    
    // Only used when newDataType is DECIMAL
    private Integer decimalPrecision;
    private Integer decimalScale;
} 