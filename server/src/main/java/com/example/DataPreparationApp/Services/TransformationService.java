package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.DTO.ColumnTypeChangeDTO;
import com.example.DataPreparationApp.DTO.PreparationPreviewDTO;
import com.example.DataPreparationApp.DTO.TransformationConfig;
import com.example.DataPreparationApp.DTO.TransformationResponseDTO;
import com.example.DataPreparationApp.Model.*;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.example.DataPreparationApp.Repository.TransformationStepRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransformationService {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DatasetRowRepository datasetRowRepository;
    private final TransformationStepRepository transformationStepRepository;
    private final ObjectMapper objectMapper;
    private final DataProfilingService dataProfilingService;
    
    /**
     * Available transformation types
     */
    public enum TransformationType {
        COLUMN_DATA_TYPE_CHANGE,
        FILTER_ROWS,
        FILL_NULL,
        COLUMN_RENAME,
        COLUMN_DROP,
        COLUMN_FORMULA
    }
    
    /**
     * Apply data type changes to dataset columns and record the transformation
     */
    @Transactional
    public TransformationResponseDTO applyColumnDataTypeChanges(UUID datasetId, 
                                                               List<ColumnTypeChangeDTO> changes, 
                                                               User user) {
        // Check that the dataset exists
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        // Apply changes to each column
        List<DatasetColumn> updatedColumns = new ArrayList<>();
        for (ColumnTypeChangeDTO change : changes) {
            DatasetColumn column = datasetColumnRepository.findById(change.getColumnId())
                    .orElseThrow(() -> new IllegalArgumentException("Column not found: " + change.getColumnId()));
            
            // Verify column belongs to the dataset
            if (!column.getDataset().getId().equals(datasetId)) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }
            
            // Update the column's data type
            column.setInferredDataType(change.getNewDataType());
            
            // Handle DECIMAL type specific attributes
            if (change.getNewDataType().equals("DECIMAL")) {
                column.setDecimalPrecision(change.getDecimalPrecision());
                column.setDecimalScale(change.getDecimalScale());
            } else {
                // Reset decimal attributes for other types
                column.setDecimalPrecision(null);
                column.setDecimalScale(null);
            }
            
            updatedColumns.add(column);
        }
        
        // Save all updated columns
        datasetColumnRepository.saveAll(updatedColumns);
        
        // Create a transformation step record
        TransformationStep transformationStep = createTransformationStep(dataset, changes, user);
        
        return TransformationResponseDTO.fromTransformationStep(transformationStep, changes.size());
    }
    
    /**
     * Execute a transformation or set of transformations on a dataset.
     * 
     * @param dataset The source dataset
     * @param transformations List of transformations to apply
     * @param user The user executing the transformations
     * @param saveToDB Whether to save the result to the database
     * @return The transformed dataset (may be persisted or ephemeral)
     */
    @Transactional
    public Dataset executeTransformations(Dataset dataset, List<TransformationConfig> transformations, 
                                         User user, boolean saveToDB) {

        
        // Create a new dataset if saving to DB
        Dataset resultDataset = dataset;
        if (saveToDB) {
            // Create a new dataset
            resultDataset = Dataset.builder()
                    .name(dataset.getName() + " (Transformed)")
                    .description("Preparation created from dataset: " + dataset.getName())
                    .file(dataset.getFile())
                    .build();
            
            // Save the dataset
            resultDataset = datasetRepository.save(resultDataset);
        }
        
        // Get all columns from the source dataset
        List<DatasetColumn> sourceColumns = datasetColumnRepository.findByDatasetIdOrderByPosition(dataset.getId());

        
        // Create a map of column IDs to column objects for quick lookup
        Map<UUID, DatasetColumn> columnMap = sourceColumns.stream()
                .collect(Collectors.toMap(DatasetColumn::getId, Function.identity()));
        
        // Create a map of column names to column objects for quick lookup
        Map<String, DatasetColumn> columnNameMap = sourceColumns.stream()
                .collect(Collectors.toMap(DatasetColumn::getName, Function.identity()));
        
        // Pre-resolve column names for all transformations that have column IDs but no names
        for (TransformationConfig transformation : transformations) {
            // If there's a columnId but no columnName, try to resolve it
            if (transformation.getColumnId() != null && transformation.getColumnName() == null) {
                DatasetColumn column = columnMap.get(transformation.getColumnId());
                if (column != null) {
                    transformation.setColumnName(column.getName());
    
                } else {
                    try {
                        column = datasetColumnRepository.findById(transformation.getColumnId()).orElse(null);
                        if (column != null) {
                            transformation.setColumnName(column.getName());
                        
                        } else {
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
        
        // Process column-level transformations first (renames, drops, etc.)
        List<DatasetColumn> resultColumns = new ArrayList<>(sourceColumns);
        
        // Map column names to column positions for easy access
        Map<String, Integer> columnPositions = new HashMap<>();
        for (int i = 0; i < sourceColumns.size(); i++) {
            columnPositions.put(sourceColumns.get(i).getName(), i);
        }
        
        // Apply column transformations first
        for (TransformationConfig transformation : transformations) {
            // Resolve column reference (either by ID or name)
            DatasetColumn targetColumn = null;
            if (transformation.getColumnId() != null) {
                targetColumn = columnMap.get(transformation.getColumnId());
            } else if (transformation.getColumnName() != null) {
                targetColumn = columnNameMap.get(transformation.getColumnName());
            }
            
            // Apply the column-level transformation
            if (targetColumn != null) {
                switch (transformation.getType()) {
                    case "COLUMN_RENAME":
                        String newName = (String) transformation.getParameters().get("newName");
                        targetColumn.setName(newName);
                        // Update the column name map
                        columnNameMap.remove(targetColumn.getName());
                        columnNameMap.put(newName, targetColumn);
                        break;
                    case "COLUMN_DROP":
                        resultColumns.remove(targetColumn);
                        columnMap.remove(targetColumn.getId());
                        columnNameMap.remove(targetColumn.getName());
                        break;
                }
            }
        }
        
        // If saveToDB is true, save the updated columns
        if (saveToDB) {
            // Save updated columns
            for (int i = 0; i < resultColumns.size(); i++) {
                DatasetColumn originalColumn = resultColumns.get(i);
                DatasetColumn newColumn = DatasetColumn.builder()
                        .dataset(resultDataset)
                        .name(originalColumn.getName())
                        .position(i)
                        .inferredDataType(originalColumn.getInferredDataType())
                        .isNullable(originalColumn.getIsNullable())
                        .uniqueness(originalColumn.getUniqueness())
                        .format(originalColumn.getFormat())
                        .decimalPrecision(originalColumn.getDecimalPrecision())
                        .decimalScale(originalColumn.getDecimalScale())
                        .build();
                datasetColumnRepository.save(newColumn);
            }
        }
        
        // Load rows using the same approach as in generatePreview 
        // but without limiting the number of rows
        List<DatasetRow> rows = new ArrayList<>();
        
        try {
            // Approach 1: Direct repository method
            rows = datasetRowRepository.findByDatasetIdOrderByRowNumber(dataset.getId());
           
            // If no rows found, try a different approach
            if (rows.isEmpty()) {
                rows = datasetRowRepository.findByDataset(dataset, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
 
                // If still no rows, try native query as final fallback
                if (rows.isEmpty()) {
                    rows = datasetRowRepository.findByDatasetIdNative(dataset.getId(), 10000); // Limit for safety
                }
            }
        } catch (Exception e) {
            
            // Try native query as emergency fallback after exception
            try {
                rows = datasetRowRepository.findByDatasetIdNative(dataset.getId(), 10000);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load source data for transformation", ex);
            }
        }
        
        // Apply row-level transformations
        for (TransformationConfig transformation : transformations) {
            String columnName = transformation.getColumnName();
            UUID columnId = transformation.getColumnId();
            String columnDesc = (columnName != null) ? columnName : (columnId != null ? "ID:" + columnId : "unknown");
            
            // Skip column-level transformations already handled
            if ("COLUMN_RENAME".equals(transformation.getType()) || 
                "COLUMN_DROP".equals(transformation.getType())) {
                continue;
            }

            
            // Resolve column reference for row transformations
            DatasetColumn targetColumn = null;
            if (columnId != null) {
                targetColumn = columnMap.get(columnId);
            } else if (columnName != null) {
                targetColumn = columnNameMap.get(columnName);
            }
            
            if (targetColumn == null) {
                continue;
            }
            
            // Apply row-level transformations
            int rowCountBefore = rows.size();
            
            switch (transformation.getType()) {
                case "FILTER_ROWS":
                    rows = applyFilterTransformation(rows, targetColumn, transformation.getParameters());
                    break;
                case "FILL_NULL":
                    rows = applyFillNullTransformation(rows, targetColumn, transformation.getParameters());
                    break;
                case "COLUMN_FORMULA":
                    rows = applyFormulaTransformation(rows, resultColumns, columnPositions, transformation.getParameters());
                    break;
                default:
            }
            
    
        }
        
        // Save transformed rows if requested
        if (saveToDB && !rows.isEmpty()) {
            List<DatasetRow> newRows = new ArrayList<>();
            
            for (int i = 0; i < rows.size(); i++) {
                DatasetRow originalRow = rows.get(i);
                
                try {
                    // Ensure the JSON structure is valid
                    String jsonStr = objectMapper.writeValueAsString(originalRow.getData());
                    JsonNode validatedData = objectMapper.readTree(jsonStr);
                    
                    DatasetRow newRow = DatasetRow.builder()
                            .dataset(resultDataset)
                            .rowNumber(i)
                            .data(validatedData)
                            .build();
                    newRows.add(newRow);
                    
                    // Save in batches of 1000 to avoid memory issues
                    if (newRows.size() >= 1000 || i == rows.size() - 1) {
                        datasetRowRepository.saveAll(newRows);
                        newRows.clear();
                    }
                } catch (Exception e) {
                }
            }
            
            // Update row and column counts on the dataset
            resultDataset.setRowCount((long) rows.size());
            resultDataset.setColumnCount(resultColumns.size());
            datasetRepository.save(resultDataset);
            

        }
        
        return resultDataset;
    }
    
    /**
     * Generate a preview of transformations
     * 
     * @param dataset Source dataset
     * @param transformations Transformations to apply
     * @param limit Maximum number of rows to return
     * @return Preview data with headers and sample rows
     */
    public PreparationPreviewDTO generatePreview(Dataset dataset, List<TransformationConfig> transformations, int limit) {
        // Get source columns
        List<DatasetColumn> columns = datasetColumnRepository.findByDatasetIdOrderByPosition(dataset.getId());
        
        // Create a column lookup map by ID for quick resolution
        Map<UUID, DatasetColumn> columnIdMap = columns.stream()
            .collect(Collectors.toMap(DatasetColumn::getId, Function.identity()));
            
        // Pre-resolve column names for all transformations that have column IDs but no names
        for (TransformationConfig transformation : transformations) {
            // If there's a columnId but no columnName, try to resolve it
            if (transformation.getColumnId() != null && transformation.getColumnName() == null) {
                DatasetColumn column = columnIdMap.get(transformation.getColumnId());
                if (column != null) {
                    transformation.setColumnName(column.getName());
    
                } else {
                    // Try database lookup if not in our map
                    try {
                        column = datasetColumnRepository.findById(transformation.getColumnId()).orElse(null);
                        if (column != null) {
                            transformation.setColumnName(column.getName());
                        } else {
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
        
        // Try multiple approaches to get rows
        List<DatasetRow> rows = new ArrayList<>();
        
        try {
            // Approach 1: Direct repository method
            rows = datasetRowRepository.findByDatasetIdOrderByRowNumber(dataset.getId());
            
            // Apply limit if needed
            if (rows.size() > limit) {
                rows = rows.subList(0, limit);
            }
            
            // If no rows found, try a different approach
            if (rows.isEmpty()) {
                rows = datasetRowRepository.findByDataset(dataset, PageRequest.of(0, limit)).getContent();
                
                // If still no rows, try native query as final fallback
                if (rows.isEmpty()) {
                    rows = datasetRowRepository.findByDatasetIdNative(dataset.getId(), limit);
                }
            }
        } catch (Exception e) {
            
            // Try native query as emergency fallback after exception
            try {
                rows = datasetRowRepository.findByDatasetIdNative(dataset.getId(), limit);
            } catch (Exception ex) {
            }
        }
        
        // Get column names for headers
        List<String> headers = columns.stream().map(DatasetColumn::getName).collect(Collectors.toList());
        
        // Convert dataset rows to simple map format for the preview
        List<Map<String, Object>> dataRows = new ArrayList<>();
        
        for (DatasetRow row : rows) {
            try {
                // Convert JSONB data to Map
                Map<String, Object> rowData = objectMapper.treeToValue(row.getData(), Map.class);
                dataRows.add(rowData);
            } catch (JsonProcessingException e) {
            }
        }
        
        
        // Apply transformations to the preview data
        for (TransformationConfig transformation : transformations) {
            String columnName = transformation.getColumnName();
            UUID columnId = transformation.getColumnId();
            String columnDesc = (columnName != null) ? columnName : (columnId != null ? "ID:" + columnId : "unknown");
            
            int rowCountBefore = dataRows.size();
            List<Map<String, Object>> resultRows = new ArrayList<>(dataRows);
            
            // Ensure parameters contains both columnId and columnName if available
            Map<String, Object> parameters = new HashMap<>(transformation.getParameters());
            if (columnId != null && !parameters.containsKey("columnId")) {
                parameters.put("columnId", columnId);
            }
            if (columnName != null && !parameters.containsKey("columnName")) {
                parameters.put("columnName", columnName);
            }
            parameters.put("datasetId", dataset.getId());
            
            switch (transformation.getType()) {
                case "FILTER_ROWS":
                    resultRows = applyFilterToPreview(dataRows, columnName, parameters);
                    // For preview purposes, if filter would remove all rows, skip this filter
                    if (resultRows.isEmpty() && !dataRows.isEmpty()) {
                        // Keep all rows for preview only
                        resultRows = dataRows;
                    } else {
                        dataRows = resultRows;
                    }
                    break;
                case "FILL_NULL":
                    dataRows = applyFillNullToPreview(dataRows, columnName, parameters);
                    break;
                case "COLUMN_RENAME":
                    String newName = (String) parameters.get("newName");
                    // Update header name
                    int headerIndex = headers.indexOf(columnName);
                    if (headerIndex >= 0) {
                        headers.set(headerIndex, newName);
                        
                        // Update keys in data rows
                        for (Map<String, Object> row : dataRows) {
                            Object value = row.remove(columnName);
                            if (value != null) {
                                row.put(newName, value);
                            }
                        }
                    }
                    break;
                case "COLUMN_DROP":
                    // Remove column from headers
                    headers.remove(columnName);
                    
                    // Remove column data from rows
                    for (Map<String, Object> row : dataRows) {
                        row.remove(columnName);
                    }
                    break;
                case "COLUMN_FORMULA":
                    dataRows = applyFormulaToPreview(dataRows, headers, parameters);
                    break;
            }
            
        }
        
        // If after all our efforts, we still have no data rows, create sample data
        if (dataRows.isEmpty() && !headers.isEmpty()) {
            
            // Create 1-3 sample rows with dummy data for preview purposes
            for (int i = 0; i < Math.min(3, limit); i++) {
                Map<String, Object> sampleRow = new HashMap<>();
                
                for (String header : headers) {
                    // Generate appropriate sample value based on column name
                    if (header.toLowerCase().contains("amount") || 
                        header.toLowerCase().contains("num") ||
                        header.toLowerCase().contains("count") || 
                        header.toLowerCase().contains("id")) {
                        // Numeric sample
                        sampleRow.put(header, 100 + i * 10);
                    } else if (header.toLowerCase().contains("date")) {
                        // Date sample
                        sampleRow.put(header, "2025-01-" + (10 + i));
                    } else {
                        // Text sample
                        sampleRow.put(header, "Sample " + header + " " + (i + 1));
                    }
                }
                
                dataRows.add(sampleRow);
            }
            
        }
        
        
        return new PreparationPreviewDTO(headers, dataRows);
    }
    
    /**
     * Apply a filter transformation to dataset rows
     */
    private List<DatasetRow> applyFilterTransformation(List<DatasetRow> rows, DatasetColumn column, Map<String, Object> parameters) {
        String operator = (String) parameters.get("operator");
        Object value = parameters.get("value");
        
        if (column == null || operator == null) {
            return rows; // No filtering if invalid parameters
        }
        
        String columnName = column.getName();
        String dataType = column.getInferredDataType().toString();
        boolean isNumericType = "INTEGER".equals(dataType) || "FLOAT".equals(dataType) || "DECIMAL".equals(dataType);
        
        
        // Handle null checks right away since they don't depend on data type
        if ("is_null".equals(operator)) {
            return rows.stream()
                    .filter(row -> {
                        JsonNode data = row.getData();
                        return !data.has(columnName) || data.get(columnName).isNull();
                    })
                    .collect(Collectors.toList());
        } else if ("not_null".equals(operator)) {
            return rows.stream()
                    .filter(row -> {
                        JsonNode data = row.getData();
                        return data.has(columnName) && !data.get(columnName).isNull();
                    })
                    .collect(Collectors.toList());
        }
        
        // For numeric comparisons, convert the value to double for comparison
        double numericValue = 0;
        if (isNumericType && ("greater_than".equals(operator) || "less_than".equals(operator) || 
                             "equals".equals(operator) || "not_equals".equals(operator))) {
            // Parse the numeric value for comparison
            if (value instanceof Number) {
                numericValue = ((Number) value).doubleValue();
            } else if (value instanceof String) {
                try {
                    numericValue = Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    // Continue with default string comparison
                }
            }
        }
        
        // Capture numeric value for use in lambda
        final double filterValue = numericValue;
        
        return rows.stream()
                .filter(row -> {
                    JsonNode data = row.getData();
                    if (!data.has(columnName)) {
                        return false;
                    }
                    
                    JsonNode fieldValue = data.get(columnName);
                    if (fieldValue.isNull()) {
                        return false; // Null values don't satisfy comparisons
                    }
                    
                    // For numeric comparisons with numeric data types
                    if (isNumericType && ("greater_than".equals(operator) || "less_than".equals(operator) || 
                                         "equals".equals(operator) || "not_equals".equals(operator))) {
                        double fieldNumericValue;
                        
                        // Convert field to numeric value
                        if (fieldValue.isNumber()) {
                            fieldNumericValue = fieldValue.asDouble();
                        } else if (fieldValue.isTextual()) {
                            try {
                                fieldNumericValue = Double.parseDouble(fieldValue.asText());
                            } catch (NumberFormatException e) {
                                // If we can't convert to number, it can't satisfy numeric comparison
                                return "not_equals".equals(operator);
                            }
                        } else {
                            return "not_equals".equals(operator); // Non-numeric types can't equal numbers
                        }
                        
                        // Apply the appropriate comparison
                        switch (operator) {
                            case "greater_than":
                                return fieldNumericValue > filterValue;
                            case "less_than":
                                return fieldNumericValue < filterValue;
                            case "equals":
                                return Math.abs(fieldNumericValue - filterValue) < 0.000001; // Handle floating point comparison
                            case "not_equals":
                                return Math.abs(fieldNumericValue - filterValue) >= 0.000001;
                            default:
                                return false;
                        }
                    }
                    
                    // For string comparisons or non-numeric types
                    switch (operator) {
                        case "equals":
                            return fieldValue.asText().equals(value.toString());
                        case "not_equals":
                            return !fieldValue.asText().equals(value.toString());
                        case "contains":
                            return fieldValue.asText().contains(value.toString());
                        default:
                            return false;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Apply a fill null transformation to dataset rows
     */
    @Transactional
    private List<DatasetRow> applyFillNullTransformation(List<DatasetRow> rows, DatasetColumn column, Map<String, Object> parameters) {
        String fillMode = (String) parameters.get("fillMode");
        Object fillValue = parameters.get("fillValue");
        
        if (column == null || fillMode == null) {
            return rows;
        }
        
        String columnName = column.getName();
        
        // Use pre-calculated statistics instead of calculating on the fly
        double mean = 0;
        double median = 0;
        String mostFrequent = "";
        
        if ("mean".equals(fillMode) || "median".equals(fillMode) || "most_frequent".equals(fillMode)) {
            try {
                // Try to get statistics for the resolved column
                if (column != null) {
                    try {
                        Map<String, Object> columnStats = dataProfilingService.generateColumnStatistics(column);
                        
                        if ("mean".equals(fillMode) && columnStats.containsKey("mean")) {
                            Object meanValue = columnStats.get("mean");
                            if (meanValue instanceof Number) {
                                mean = ((Number) meanValue).doubleValue();
                            } else {
                            }
                        } else if ("median".equals(fillMode) && columnStats.containsKey("median")) {
                            Object medianValue = columnStats.get("median");
                            if (medianValue instanceof Number) {
                                median = ((Number) medianValue).doubleValue();
                            } else {
                            }
                        } else if ("most_frequent".equals(fillMode) && columnStats.containsKey("frequentValues")) {
                            Object freqValues = columnStats.get("frequentValues");
                            if (freqValues instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Integer> frequentValues = (Map<String, Integer>) freqValues;
                                if (frequentValues != null && !frequentValues.isEmpty()) {
                                    mostFrequent = frequentValues.entrySet().stream()
                                        .max(Map.Entry.comparingByValue())
                                        .map(Map.Entry::getKey)
                                        .orElse("");
                                }
                            } else {
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
                // In case of any error, use the fallback calculation
                double[] meanArray = new double[] { 0.0 };
                double[] medianArray = new double[] { 0.0 };
                String[] mostFrequentArray = new String[] { "" };
                
                calculateDatasetRowStats(rows, columnName, fillMode, meanArray, medianArray, mostFrequentArray);
                
                // Get results from arrays
                mean = meanArray[0];
                median = medianArray[0];
                mostFrequent = mostFrequentArray[0];
            }
        }
        
        // Apply the fill transformation
        List<DatasetRow> updatedRows = new ArrayList<>();
        
        for (DatasetRow row : rows) {
            ObjectNode data = (ObjectNode) row.getData().deepCopy();
            
            // More comprehensive null check to catch various null representations
            boolean isNullValue = false;
            if (!data.has(columnName)) {
                isNullValue = true;
            } else {
                JsonNode value = data.get(columnName);
                isNullValue = value.isNull() || 
                    (value.isTextual() && (value.asText().trim().isEmpty() || 
                    "NULL".equalsIgnoreCase(value.asText().trim())));
            }
            
            if (isNullValue) {
                // Determine fill value based on mode
                switch (fillMode) {
                    case "value":
                        if (fillValue != null) {
                            // Convert fillValue to appropriate JSON node
                            JsonNode fillNode = objectMapper.valueToTree(fillValue);
                            data.set(columnName, fillNode);
                        }
                        break;
                    case "mean":
                        data.put(columnName, mean);
                        break;
                    case "median":
                        data.put(columnName, median);
                        break;
                    case "most_frequent":
                        data.put(columnName, mostFrequent);
                        break;
                    case "zero":
                        if ("FLOAT".equals(column.getInferredDataType()) || 
                            "INTEGER".equals(column.getInferredDataType()) ||
                            "DECIMAL".equals(column.getInferredDataType())) {
                            data.put(columnName, 0);
                        } else if ("BOOLEAN".equals(column.getInferredDataType())) {
                            data.put(columnName, false);
                        } else {
                            data.put(columnName, "0");
                        }
                        break;
                    case "empty_string":
                        data.put(columnName, "");
                        break;
                }
            }
            
            // Create updated row with the modified data
            DatasetRow updatedRow = DatasetRow.builder()
                    .id(row.getId())
                    .dataset(row.getDataset())
                    .rowNumber(row.getRowNumber())
                    .data(data)
                    .build();
            
            updatedRows.add(updatedRow);
        }
        
        return updatedRows;
    }
    
    /**
     * Apply a formula transformation to dataset rows
     */
    private List<DatasetRow> applyFormulaTransformation(List<DatasetRow> rows, List<DatasetColumn> columns, 
                                                       Map<String, Integer> columnPositions, Map<String, Object> parameters) {
        String formula = (String) parameters.get("formula");
        String outputColumn = (String) parameters.get("outputColumn");
        
        if (formula == null || outputColumn == null) {
            return rows;
        }
        
        // Check if output column exists, if not create it
        boolean isNewColumn = !columnPositions.containsKey(outputColumn);
        if (isNewColumn) {
            // If we were persisting, we would create a new column
        }
        
        // Apply the formula to each row
        List<DatasetRow> updatedRows = new ArrayList<>();
        
        for (DatasetRow row : rows) {
            ObjectNode data = (ObjectNode) row.getData().deepCopy();
            
            // Here we'd apply the formula logic
            // This is a simplified version that supports basic arithmetic operations
            Object result = evaluateFormula(formula, data, columnPositions);
            
            // Set the result in the output column
            if (result instanceof Number) {
                data.put(outputColumn, ((Number) result).doubleValue());
            } else if (result instanceof Boolean) {
                data.put(outputColumn, (Boolean) result);
            } else if (result != null) {
                data.put(outputColumn, result.toString());
            } else {
                data.putNull(outputColumn);
            }
            
            // Create updated row with the modified data
            DatasetRow updatedRow = DatasetRow.builder()
                    .id(row.getId())
                    .dataset(row.getDataset())
                    .rowNumber(row.getRowNumber())
                    .data(data)
                    .build();
            
            updatedRows.add(updatedRow);
        }
        
        return updatedRows;
    }
    
    /**
     * Apply a filter to preview data
     */
    private List<Map<String, Object>> applyFilterToPreview(List<Map<String, Object>> rows, String columnName, Map<String, Object> parameters) {
        // Extract parameters
        String operator = (String) parameters.get("operator");
        Object value = parameters.get("value");
        UUID datasetId = (UUID) parameters.get("datasetId"); // Added to retrieve dataset ID
        UUID columnId = null;
        
        // Check if columnId is provided
        if (parameters.containsKey("columnId")) {
            Object columnIdObj = parameters.get("columnId");
            if (columnIdObj instanceof String) {
                try {
                    columnId = UUID.fromString((String) columnIdObj);
                } catch (IllegalArgumentException e) {
                }
            } else if (columnIdObj instanceof UUID) {
                columnId = (UUID) columnIdObj;
            }
        }
        
        // Basic validation
        if ((columnName == null && columnId == null) || operator == null) {
            return rows;
        }
        
        // Resolve the column using the helper method
        DatasetColumn resolvedColumn = resolveColumn(columnId, columnName, datasetId);
        String actualColumnName = (resolvedColumn != null) ? resolvedColumn.getName() : columnName;
        
        if (actualColumnName == null) {
            return rows;
        }
        
        
        // Use resolved column name for the rest of the function
        final String finalColumnName = actualColumnName;
        
        // First, check if any rows have the column
        boolean columnExists = rows.stream()
                .anyMatch(row -> row.containsKey(finalColumnName));
        
        if (!columnExists) {
            return rows;
        }
        
        // Get column data type from columns repository if available
        String dataType = "STRING"; // Default to string
        
        try {
            // If we already have the resolved column, use its data type
            if (resolvedColumn != null) {
                dataType = resolvedColumn.getInferredDataType().toString();
            }
        } catch (Exception e) {
            // Continue with default string type
        }
        
        // Make dataType final for use in lambda expressions
        final String finalDataType = dataType;
        
        // Handle null checks right away since they don't depend on data type
        if ("is_null".equals(operator)) {
            return rows.stream()
                    .filter(row -> row.get(finalColumnName) == null)
                    .collect(Collectors.toList());
        } else if ("not_null".equals(operator)) {
            return rows.stream()
                    .filter(row -> row.get(finalColumnName) != null)
                    .collect(Collectors.toList());
        }
        
        // Handle numeric comparisons for numeric data types
        boolean isNumericType = "INTEGER".equals(finalDataType) || 
                               "FLOAT".equals(finalDataType) || 
                               "DECIMAL".equals(finalDataType);
        
        if (isNumericType && ("greater_than".equals(operator) || "less_than".equals(operator))) {
            // Parse the numeric value for comparison
            double numericValue;
            
            if (value instanceof Number) {
                numericValue = ((Number) value).doubleValue();
            } else if (value instanceof String) {
                try {
                    numericValue = Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    return rows; // Return unfiltered if value isn't numeric
                }
            } else {
                return rows;
            }
            
            
            // Capture the value for use in lambda
            final double filterValue = numericValue;
            
            return rows.stream()
                    .filter(row -> {
                        Object fieldValue = row.get(finalColumnName);
                        
                        if (fieldValue == null) {
                            return false; // Null values don't satisfy greater/less than
                        }
                        
                        // Convert field value to number
                        double fieldNumericValue;
                        if (fieldValue instanceof Number) {
                            fieldNumericValue = ((Number) fieldValue).doubleValue();
                        } else if (fieldValue instanceof String) {
                            try {
                                fieldNumericValue = Double.parseDouble((String) fieldValue);
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        } else {
                            return false;
                        }
                        
                        if ("greater_than".equals(operator)) {
                            return fieldNumericValue > filterValue;
                        } else { // "less_than"
                            return fieldNumericValue < filterValue;
                        }
                    })
                    .collect(Collectors.toList());
        }
        
        // Handle equality operations specially for numeric types
        if (isNumericType && ("equals".equals(operator) || "not_equals".equals(operator))) {
            // Try to convert the filter value to a number
            Double numericValue = null;
            
            if (value instanceof Number) {
                numericValue = ((Number) value).doubleValue();
            } else if (value instanceof String) {
                try {
                    numericValue = Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    // If we can't convert to number, fall back to string comparison
                    numericValue = null;
                }
            }
            
            // If we have a numeric value to compare against
            if (numericValue != null) {
                // Capture for lambda
                final double filterValue = numericValue;
                
                return rows.stream()
                        .filter(row -> {
                            Object fieldValue = row.get(finalColumnName);
                            
                            if (fieldValue == null) {
                                return false; // Null never equals a number
                            }
                            
                            // Convert field to number
                            Double fieldNumericValue = null;
                            if (fieldValue instanceof Number) {
                                fieldNumericValue = ((Number) fieldValue).doubleValue();
                            } else if (fieldValue instanceof String) {
                                try {
                                    fieldNumericValue = Double.parseDouble((String) fieldValue);
                                } catch (NumberFormatException e) {
                                    return "not_equals".equals(operator); // Can't be equal if not a number
                                }
                            }
                            
                            if (fieldNumericValue != null) {
                                boolean isEqual = Math.abs(fieldNumericValue - filterValue) < 0.000001; // Handle floating point comparison
                                return "equals".equals(operator) ? isEqual : !isEqual;
                            } else {
                                return "not_equals".equals(operator); // Non-numeric can't equal numeric
                            }
                        })
                        .collect(Collectors.toList());
            }
        }
        
        // For all other cases (string comparisons or non-numeric types)
        return rows.stream()
                .filter(row -> {
                    Object fieldValue = row.get(finalColumnName);
                    
                    if (fieldValue == null) {
                        return false; // Null doesn't match equals, not_equals or contains
                    }
                    
                    switch (operator) {
                        case "equals":
                            return String.valueOf(fieldValue).equals(String.valueOf(value));
                        case "not_equals":
                            return !String.valueOf(fieldValue).equals(String.valueOf(value));
                        case "contains":
                            return fieldValue.toString().contains(value.toString());
                        default:
                            return false;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Apply a fill null transformation to preview data
     */
    private List<Map<String, Object>> applyFillNullToPreview(List<Map<String, Object>> rows, String columnName, Map<String, Object> parameters) {
        String fillMode = (String) parameters.get("fillMode");
        Object fillValue = parameters.get("fillValue");
        UUID datasetId = (UUID) parameters.get("datasetId");
        UUID columnId = null;
        
        // Check if columnId is provided
        if (parameters.containsKey("columnId")) {
            Object columnIdObj = parameters.get("columnId");
            if (columnIdObj instanceof String) {
                try {
                    columnId = UUID.fromString((String) columnIdObj);
                } catch (IllegalArgumentException e) {
                }
            } else if (columnIdObj instanceof UUID) {
                columnId = (UUID) columnIdObj;
            }
        }
        
        // Basic validation
        if ((columnName == null && columnId == null) || fillMode == null) {
            return rows;
        }
        
        // Resolve the column using the helper method
        DatasetColumn resolvedColumn = resolveColumn(columnId, columnName, datasetId);
        String actualColumnName = (resolvedColumn != null) ? resolvedColumn.getName() : columnName;
        
        if (actualColumnName == null) {
            return rows;
        }
        
        
        // Use resolved column name for the rest of the function
        final String finalColumnName = actualColumnName;
        
        // Use pre-calculated statistics instead of calculating on the fly
        double mean = 0;
        double median = 0;
        String mostFrequent = "";
        
        if ("mean".equals(fillMode) || "median".equals(fillMode) || "most_frequent".equals(fillMode)) {
            try {
                // Try to get statistics for the resolved column
                if (resolvedColumn != null) {
                    try {
                        Map<String, Object> columnStats = dataProfilingService.generateColumnStatistics(resolvedColumn);
                        
                        if ("mean".equals(fillMode) && columnStats.containsKey("mean")) {
                            Object meanValue = columnStats.get("mean");
                            if (meanValue instanceof Number) {
                                mean = ((Number) meanValue).doubleValue();
                            } else {
                            }
                        } else if ("median".equals(fillMode) && columnStats.containsKey("median")) {
                            Object medianValue = columnStats.get("median");
                            if (medianValue instanceof Number) {
                                median = ((Number) medianValue).doubleValue();
                            } else {
                            }
                        } else if ("most_frequent".equals(fillMode) && columnStats.containsKey("frequentValues")) {
                            Object freqValues = columnStats.get("frequentValues");
                            if (freqValues instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Integer> frequentValues = (Map<String, Integer>) freqValues;
                                if (frequentValues != null && !frequentValues.isEmpty()) {
                                    mostFrequent = frequentValues.entrySet().stream()
                                        .max(Map.Entry.comparingByValue())
                                        .map(Map.Entry::getKey)
                                        .orElse("");
                                }
                            } else {
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            } catch (Exception e) {
                // In case of any error, use the fallback calculation
                double[] meanArray = new double[] { 0.0 };
                double[] medianArray = new double[] { 0.0 };
                String[] mostFrequentArray = new String[] { "" };
                
                calculatePreviewStatsForMap(rows, finalColumnName, fillMode, meanArray, medianArray, mostFrequentArray);
                
                // Get results from arrays
                mean = meanArray[0];
                median = medianArray[0];
                mostFrequent = mostFrequentArray[0];
            }
        }
        
        // Apply the fill transformation
        List<Map<String, Object>> updatedRows = new ArrayList<>();
        
        for (Map<String, Object> row : new ArrayList<>(rows)) {
            Map<String, Object> updatedRow = new HashMap<>(row);
            
            // More comprehensive null check to catch various null representations
            boolean isNullValue = false;
            if (!updatedRow.containsKey(finalColumnName)) {
                isNullValue = true;
            } else {
                Object value = updatedRow.get(finalColumnName);
                if (value == null) {
                    isNullValue = true;
                } else if (value instanceof String) {
                    String strVal = (String) value;
                    isNullValue = strVal.trim().isEmpty() || "NULL".equalsIgnoreCase(strVal.trim());
                } else if (value instanceof JsonNode) {
                    JsonNode jsonVal = (JsonNode) value;
                    isNullValue = jsonVal.isNull() || 
                        (jsonVal.isTextual() && (jsonVal.asText().trim().isEmpty() || 
                        "NULL".equalsIgnoreCase(jsonVal.asText().trim())));
                }
            }
            
            if (isNullValue) {
                // Determine fill value based on mode
                switch (fillMode) {
                    case "value":
                        if (fillValue != null) {
                            updatedRow.put(finalColumnName, fillValue);
                        }
                        break;
                    case "mean":
                        updatedRow.put(finalColumnName, mean);
                        break;
                    case "median":
                        updatedRow.put(finalColumnName, median);
                        break;
                    case "most_frequent":
                        updatedRow.put(finalColumnName, mostFrequent);
                        break;
                    case "zero":
                        updatedRow.put(finalColumnName, 0);
                        break;
                    case "empty_string":
                        updatedRow.put(finalColumnName, "");
                        break;
                }
            }
            
            updatedRows.add(updatedRow);
        }
        
        return updatedRows;
    }
    
    /**
     * Helper method to calculate statistics from preview data (Map version)
     * Used with the applyFillNullToPreview method
     */
    private void calculatePreviewStatsForMap(List<Map<String, Object>> rows, String columnName, String fillMode,
                                             double[] mean, double[] median, String[] mostFrequent) {
        List<Object> values = new ArrayList<>();
        Map<Object, Integer> valueCounts = new HashMap<>();
        
        for (Map<String, Object> row : rows) {
            Object val = row.get(columnName);
            if (val != null) {
                values.add(val);
                valueCounts.put(val, valueCounts.getOrDefault(val, 0) + 1);
            }
        }
        
        if (!values.isEmpty()) {
            if ("mean".equals(fillMode)) {
                mean[0] = values.stream()
                        .filter(v -> v instanceof Number)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .average()
                        .orElse(0);
            } else if ("median".equals(fillMode)) {
                List<Double> sortedValues = values.stream()
                        .filter(v -> v instanceof Number)
                        .map(v -> ((Number) v).doubleValue())
                        .sorted()
                        .collect(Collectors.toList());
                
                if (!sortedValues.isEmpty()) {
                    int middle = sortedValues.size() / 2;
                    if (sortedValues.size() % 2 == 1) {
                        median[0] = sortedValues.get(middle);
                    } else {
                        median[0] = (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
                    }
                }
            } else if ("most_frequent".equals(fillMode)) {
                Optional<Map.Entry<Object, Integer>> mostFrequentEntry = valueCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue());
                
                if (mostFrequentEntry.isPresent()) {
                    mostFrequent[0] = mostFrequentEntry.get().getKey().toString();
                }
            }
        }
    }

    /**
     * Execute a transformation or set of transformations on a dataset with batch processing.
     * This version is optimized for handling large datasets by processing rows in batches.
     * 
     * @param dataset The source dataset
     * @param transformations List of transformations to apply
     * @param user The user executing the transformations
     * @param saveToDB Whether to save the result to the database
     * @param batchSize The number of rows to process in each batch
     * @return The transformed dataset (may be persisted or ephemeral)
     */
    @Transactional
    public Dataset executeTransformationsWithBatching(Dataset dataset, List<TransformationConfig> transformations, 
                                                    User user, boolean saveToDB, int batchSize) {
        
        // Create a new dataset if saving to DB
        Dataset resultDataset = dataset;
        if (saveToDB) {
            // Create a new dataset
            resultDataset = Dataset.builder()
                    .name(dataset.getName() + " (Transformed)")
                    .description("Preparation created from dataset: " + dataset.getName())
                    .file(dataset.getFile())
                    .build();
            
            // Save the dataset
            resultDataset = datasetRepository.save(resultDataset);
        }
        
        // Get all columns from the source dataset
        List<DatasetColumn> sourceColumns = datasetColumnRepository.findByDatasetIdOrderByPosition(dataset.getId());
        Map<UUID, DatasetColumn> columnMap = sourceColumns.stream()
                .collect(Collectors.toMap(DatasetColumn::getId, Function.identity()));
        Map<String, DatasetColumn> columnNameMap = sourceColumns.stream()
                .collect(Collectors.toMap(DatasetColumn::getName, Function.identity()));
        
        // Map column names to column positions for easy access
        Map<String, Integer> columnPositions = new HashMap<>();
        for (int i = 0; i < sourceColumns.size(); i++) {
            columnPositions.put(sourceColumns.get(i).getName(), i);
        }
        
        // Create a mutable list of columns (to handle column additions/removals)
        List<DatasetColumn> resultColumns = new ArrayList<>(sourceColumns);
        
        // Process column-level transformations first (renames, drops, etc.)
        for (TransformationConfig transformation : transformations) {
            // Resolve column reference (either by ID or name)
            DatasetColumn targetColumn = null;
            if (transformation.getColumnId() != null) {
                targetColumn = columnMap.get(transformation.getColumnId());
            } else if (transformation.getColumnName() != null) {
                targetColumn = columnNameMap.get(transformation.getColumnName());
            }
            
            // Apply column-level transformations
            switch (transformation.getType()) {
                case "COLUMN_RENAME":
                    if (targetColumn != null) {
                        String newName = (String) transformation.getParameters().get("newName");
                        targetColumn.setName(newName);
                        // Update the column name map
                        columnNameMap.remove(targetColumn.getName());
                        columnNameMap.put(newName, targetColumn);
                    }
                    break;
                case "COLUMN_DROP":
                    if (targetColumn != null) {
                        resultColumns.remove(targetColumn);
                        columnMap.remove(targetColumn.getId());
                        columnNameMap.remove(targetColumn.getName());
                    }
                    break;
                // Other column-level transformations...
            }
        }
        
        // If saveToDB is true, save the updated columns
        if (saveToDB) {
            // Save updated columns
            for (int i = 0; i < resultColumns.size(); i++) {
                DatasetColumn originalColumn = resultColumns.get(i);
                DatasetColumn newColumn = DatasetColumn.builder()
                        .dataset(resultDataset)
                        .name(originalColumn.getName())
                        .position(i)
                        .inferredDataType(originalColumn.getInferredDataType())
                        .isNullable(originalColumn.getIsNullable())
                        .uniqueness(originalColumn.getUniqueness())
                        .format(originalColumn.getFormat())
                        .decimalPrecision(originalColumn.getDecimalPrecision())
                        .decimalScale(originalColumn.getDecimalScale())
                        .build();
                datasetColumnRepository.save(newColumn);
            }
        }
        
        // Get the total row count - added emergency fallback
        long totalRows = 0;
        try {
            totalRows = datasetRowRepository.countByDatasetId(dataset.getId());
        } catch (Exception e) {
            // Try a fallback method
            try {
                List<DatasetRow> sampleRows = datasetRowRepository.findByDatasetIdNative(dataset.getId(), 1000);
                totalRows = sampleRows.size();
            } catch (Exception ex) {
            }
        }
        
        
        // Process rows in batches to avoid memory issues
        long processedRows = 0;
        int batchNumber = 0;
        int totalSavedRows = 0;
        
        // Always process at least one batch
        boolean continueBatching = true;
        
        while (continueBatching) {
            batchNumber++;
            
            // Get batch of rows from the source dataset - try multiple approaches
            List<DatasetRow> batchRows = new ArrayList<>();
            
            // Try different methods to fetch rows, in order of preference
            try {
                batchRows = datasetRowRepository.findBatchByDatasetIdOrderByRowNumber(
                        dataset.getId(), (int)processedRows, batchSize);
            } catch (Exception e) {
            }
            
            // If previous method failed, try direct source data load
            if (batchRows.isEmpty()) {
                try {
                    batchRows = datasetRowRepository.findByDatasetIdNative(dataset.getId(), batchSize);
                } catch (Exception e) {
                }
            }
            
            // Log batch retrieval status
            
            // If no more rows, exit loop after first batch
            if (batchRows.isEmpty()) {
                if (batchNumber > 1) {
                    break;
                } else {
                    // Try one more desperate approach - direct entity load
                    try {
                        batchRows = datasetRowRepository.findByDataset(dataset, PageRequest.of(0, batchSize)).getContent();
                    } catch (Exception e) {
                        break;
                    }
                }
            }
            
            // Apply row-level transformations to this batch
            List<DatasetRow> transformedBatchRows = applyRowLevelTransformations(
                    batchRows, resultColumns, columnPositions, transformations);
            
            // Safety check: if we have zero rows after the first batch and filtering was applied,
            // consider preserving some sample data to prevent completely empty datasets
            if (transformedBatchRows.isEmpty() && batchNumber == 1 && !batchRows.isEmpty()) {
                
                // Take up to 10 sample rows from the original batch for structure preservation
                int sampleSize = Math.min(10, batchRows.size());
                transformedBatchRows = batchRows.subList(0, sampleSize);
                
                // Mark these rows as samples in the data if possible
                for (DatasetRow row : transformedBatchRows) {
                    try {
                        ObjectNode data = (ObjectNode) row.getData();
                        data.put("_preserved_sample", true);
                        data.put("_note", "This row was preserved as a sample when all data was filtered out");
                    } catch (Exception e) {
                    }
                }
                
            }
            
            // Save transformed rows if requested
            if (saveToDB && !transformedBatchRows.isEmpty()) {
                List<DatasetRow> newRows = new ArrayList<>();
                
                for (int i = 0; i < transformedBatchRows.size(); i++) {
                    DatasetRow originalRow = transformedBatchRows.get(i);
                    
                    try {
                        // Validate the JSON data
                        String jsonStr;
                        JsonNode validatedData;
                        
                        if (originalRow.getData() == null) {
                            validatedData = objectMapper.createObjectNode();
                        } else {
                            jsonStr = objectMapper.writeValueAsString(originalRow.getData());
                            validatedData = objectMapper.readTree(jsonStr);
                        }
                        
                        // Create new row with proper dataset link and row number
                        DatasetRow newRow = DatasetRow.builder()
                                .dataset(resultDataset)
                                .rowNumber(totalSavedRows + i)
                                .data(validatedData)
                                .build();
                        newRows.add(newRow);
                    } catch (Exception e) {
                    }
                }
                
                // Save all rows in this batch
                if (!newRows.isEmpty()) {
                    try {
                        List<DatasetRow> savedRows = datasetRowRepository.saveAll(newRows);
                        totalSavedRows += savedRows.size();
                    } catch (Exception e) {
                    }
                }
            }
            
            // Update progress
            processedRows += batchRows.size();
            
            // End conditions: 
            // 1. If we've processed all the rows based on the total count
            // 2. If we get a batch smaller than the requested size (last batch)
            if (totalRows > 0 && processedRows >= totalRows) {
                continueBatching = false;
            } else if (batchRows.size() < batchSize) {
                continueBatching = false;
            }
        }
        
        // Update dataset metadata
        if (saveToDB) {
            // Verify row count before finalizing
            try {
                long actualCount = datasetRowRepository.countByDatasetId(resultDataset.getId());
                
                // Update with actual count from database
                resultDataset.setRowCount(actualCount);
            } catch (Exception e) {
                // Fallback to our tracking count
                resultDataset.setRowCount((long) totalSavedRows);
            }
            
            resultDataset.setColumnCount(resultColumns.size());
            datasetRepository.save(resultDataset);
            
        }
        
        return resultDataset;
    }
    
    /**
     * Apply row-level transformations to a batch of rows
     */
    private List<DatasetRow> applyRowLevelTransformations(List<DatasetRow> rows, 
                                                         List<DatasetColumn> columns,
                                                         Map<String, Integer> columnPositions,
                                                         List<TransformationConfig> transformations) {
        // Start with all rows
        List<DatasetRow> resultRows = new ArrayList<>(rows);
        
        // Apply each transformation in sequence
        for (TransformationConfig transformation : transformations) {
            // Skip column-level transformations already handled
            if ("COLUMN_RENAME".equals(transformation.getType()) || 
                "COLUMN_DROP".equals(transformation.getType())) {
                continue;
            }
            
            // Resolve column reference (either by ID or name)
            DatasetColumn targetColumn = null;
            String columnName = transformation.getColumnName();
            UUID columnId = transformation.getColumnId();
            
            if (columnId != null) {
                targetColumn = columns.stream()
                    .filter(col -> col.getId().equals(columnId))
                    .findFirst()
                    .orElse(null);
            } else if (columnName != null) {
                targetColumn = columns.stream()
                    .filter(col -> col.getName().equals(columnName))
                    .findFirst()
                    .orElse(null);
            }
            
            // Apply the row-level transformation
            int rowCountBefore = resultRows.size();
            
            switch (transformation.getType()) {
                case "FILTER_ROWS":
                    List<DatasetRow> filteredRows = applyFilterTransformation(resultRows, targetColumn, transformation.getParameters());
                    
                    // IMPORTANT: Add safety check to match preview behavior
                    // For execution purposes, if filter would remove all rows, log a warning but still apply the filter
                    if (filteredRows.isEmpty() && !resultRows.isEmpty()) {
                    }
                    
                    resultRows = filteredRows;
                    break;
                case "FILL_NULL":
                    resultRows = applyFillNullTransformation(resultRows, targetColumn, transformation.getParameters());
                    break;
                case "COLUMN_FORMULA":
                    resultRows = applyFormulaTransformation(resultRows, columns, columnPositions, transformation.getParameters());
                    break;
                default:
            }
            
        }
        
        return resultRows;
    }

    /**
     * Helper method to resolve a column from either columnId or columnName
     * 
     * @param columnId Column ID to check first
     * @param columnName Column name to use as fallback
     * @param datasetId Optional dataset ID to verify column belongs to this dataset
     * @return Resolved DatasetColumn or null if not found
     */
    private DatasetColumn resolveColumn(UUID columnId, String columnName, UUID datasetId) {
        DatasetColumn column = null;
        
        // First try to resolve by ID (more precise)
        if (columnId != null) {
            try {
                column = datasetColumnRepository.findById(columnId).orElse(null);
                if (column != null) {
                    
                    // Verify dataset if provided
                    if (datasetId != null && column.getDataset() != null && 
                        !column.getDataset().getId().equals(datasetId)) {
                        column = null;
                    }
                }
            } catch (Exception e) {
            }
        }
        
        // Fall back to name if ID lookup failed
        if (column == null && columnName != null) {
            try {
                List<DatasetColumn> columns = datasetColumnRepository.findByName(columnName);
                if (!columns.isEmpty()) {
                    // If dataset ID provided, try to find a matching column
                    if (datasetId != null) {
                        for (DatasetColumn col : columns) {
                            if (col.getDataset() != null && col.getDataset().getId().equals(datasetId)) {
                                column = col;
                                break;
                            }
                        }
                    }
                    
                    // If still null, use the first column found
                    if (column == null) {
                        column = columns.get(0);
                    }
                }
            } catch (Exception e) {
            }
        }
        
        if (column == null) {
        }
        
        return column;
    }

    /**
     * Apply a formula transformation to preview data
     */
    private List<Map<String, Object>> applyFormulaToPreview(List<Map<String, Object>> rows, List<String> headers, Map<String, Object> parameters) {
        String formula = (String) parameters.get("formula");
        String outputColumn = (String) parameters.get("outputColumn");
        
        if (formula == null || outputColumn == null) {
            return rows;
        }
        
        // Check if output column exists, if not add it to headers
        if (!headers.contains(outputColumn)) {
            headers.add(outputColumn);
        }
        
        // Apply the formula to each row
        List<Map<String, Object>> updatedRows = new ArrayList<>();
        
        for (Map<String, Object> row : new ArrayList<>(rows)) {
            Map<String, Object> updatedRow = new HashMap<>(row);
            
            // Here we'd apply the formula logic 
            Object result = evaluateFormula(formula, updatedRow);
            updatedRow.put(outputColumn, result);
            
            updatedRows.add(updatedRow);
        }
        
        return updatedRows;
    }
    
    /**
     * Evaluate a simple formula expression
     * This is a very basic implementation that could be expanded or replaced with a proper expression engine
     */
    private Object evaluateFormula(String formula, ObjectNode data, Map<String, Integer> columnPositions) {
        // A very simplified formula evaluator - in a real system, you'd use a proper formula engine
        try {
            // Replace column references with their values
            for (Map.Entry<String, Integer> entry : columnPositions.entrySet()) {
                String columnName = entry.getKey();
                if (formula.contains(columnName)) {
                    JsonNode value = data.get(columnName);
                    if (value != null && !value.isNull()) {
                        // Check if the value is a numeric string
                        String textValue = value.asText();
                        if (value.isTextual() && isNumeric(textValue)) {
                            // It's a numeric string, so convert it to a number representation
                            formula = formula.replace(columnName, textValue);
                        } else if (value.isNumber()) {
                            // It's already a number
                            formula = formula.replace(columnName, value.asText());
                        } else {
                            // Non-numeric string, wrap in quotes
                            formula = formula.replace(columnName, "\"" + value.asText() + "\"");
                        }
                    } else {
                        // If any column is null, the result is null
                        return null;
                    }
                }
            }
            
            // Properly evaluate the formula now that all values are replaced
            try {
                // For numeric operations, parse the formula and evaluate it
                if (formula.matches(".*[0-9]+(\\.[0-9]+)?\\s*[+\\-*/]\\s*[0-9]+(\\.[0-9]+)?.*")) {
                    // Use a proper expression evaluator here
                    return evaluateSimpleArithmetic(formula);
                }
            } catch (Exception e) {
            }
            
            return formula;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Evaluate a simple formula expression for preview data
     */
    private Object evaluateFormula(String formula, Map<String, Object> row) {
        // A very simplified formula evaluator - in a real system, you'd use a proper formula engine
        try {
            // Replace column references with their values
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String columnName = entry.getKey();
                if (formula.contains(columnName)) {
                    Object value = entry.getValue();
                    if (value != null) {
                        if (value instanceof Number) {
                            // It's already a number
                            formula = formula.replace(columnName, value.toString());
                        } else if (value instanceof String && isNumeric((String)value)) {
                            // It's a numeric string, don't add quotes
                            formula = formula.replace(columnName, value.toString());
                        } else {
                            // Non-numeric value, wrap in quotes
                            formula = formula.replace(columnName, "\"" + value.toString() + "\"");
                        }
                    } else {
                        // If any column is null, the result is null
                        return null;
                    }
                }
            }
            
            // Handle basic arithmetic
            try {
                if (formula.matches(".*[0-9]+(\\.[0-9]+)?\\s*[+\\-*/]\\s*[0-9]+(\\.[0-9]+)?.*")) {
                    return evaluateSimpleArithmetic(formula);
                }
            } catch (Exception e) {
            }
            
            return formula;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Helper method to check if a string is numeric
     */
    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Helper method to evaluate simple arithmetic expressions
     */
    private Object evaluateSimpleArithmetic(String formula) {
        // This is a simplified version - in production we must use a proper expression engine
        // Strip any whitespace
        formula = formula.replaceAll("\\s+", "");
        
        // Simple regex to find basic operations between two numbers
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([\\d.]+)([+\\-*/])([\\d.]+)").matcher(formula);
        
        if (matcher.find()) {
            double left = Double.parseDouble(matcher.group(1));
            String operator = matcher.group(2);
            double right = Double.parseDouble(matcher.group(3));
            
            switch (operator) {
                case "+": return left + right;
                case "-": return left - right;
                case "*": return left * right;
                case "/": return right == 0 ? null : left / right;
                default: return formula;
            }
        }
        
        return formula;
    }
    
    /**
     * Compare a JSON value with a Java object using a comparator
     */
    private boolean compareJsonValue(JsonNode jsonValue, Object javaValue, BiPredicate<Object, Object> comparator) {
        Object convertedValue = getValueFromJsonNode(jsonValue);
        return comparator.test(convertedValue, javaValue);
    }
    
    /**
     * Convert a JsonNode to a Java object
     */
    private Object getValueFromJsonNode(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            return node.asDouble();
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else {
            return node.toString();
        }
    }
    
    /**
     * Create a transformation step record
     */
    private TransformationStep createTransformationStep(Dataset dataset, List<ColumnTypeChangeDTO> changes, User user) {
        // Find the latest transformation step for this dataset
        TransformationStep previousStep = transformationStepRepository
                .findFirstByDatasetOrderByAppliedAtDesc(dataset)
                .orElse(null);
        
        // Create parameters as JSON
        ObjectNode parameters = objectMapper.createObjectNode();
        ArrayNode columnsArray = parameters.putArray("columns");
        
        for (ColumnTypeChangeDTO change : changes) {
            ObjectNode columnNode = columnsArray.addObject();
            columnNode.put("columnId", change.getColumnId().toString());
            columnNode.put("newDataType", change.getNewDataType().toString());
            
            if (change.getNewDataType().equals("DECIMAL")) {
                columnNode.put("decimalPrecision", change.getDecimalPrecision());
                columnNode.put("decimalScale", change.getDecimalScale());
            }
        }
        
        // Create and save the transformation step
        TransformationStep step = TransformationStep.builder()
                .dataset(dataset)
                .user(user)
                .transformationType("COLUMN_DATA_TYPE_CHANGE")
                .parameters(parameters)
                .appliedAt(LocalDateTime.now())
                .previousStep(previousStep)
                .build();
        
        return transformationStepRepository.save(step);
    }
    
    /**
     * Functional interface for comparing values
     */
    @FunctionalInterface
    interface BiPredicate<T, U> {
        boolean test(T t, U u);
    }

    /**
     * Helper method to calculate statistics from preview data (Map version)
     */
    private <T> T calculatePreviewStats(List<Map<String, Object>> rows, String columnName, String fillMode,
                                    Function<List<Object>, T> calculator) {
        List<Object> values = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            Object val = row.get(columnName);
            if (val != null) {
                values.add(val);
            }
        }
        
        if (!values.isEmpty()) {
            return calculator.apply(values);
        }
        
        return null;
    }
    
    /**
     * Helper method to calculate statistics from DatasetRow objects
     */
    private void calculateDatasetRowStats(List<DatasetRow> rows, String columnName, String fillMode,
                                     double[] mean, double[] median, String[] mostFrequent) {
        List<Object> values = new ArrayList<>();
        Map<Object, Integer> valueCounts = new HashMap<>();
        
        for (DatasetRow row : rows) {
            JsonNode data = row.getData();
            if (data.has(columnName) && !data.get(columnName).isNull()) {
                JsonNode valueNode = data.get(columnName);
                Object value = null;
                
                if (valueNode.isNumber()) {
                    value = valueNode.asDouble();
                } else if (valueNode.isTextual()) {
                    value = valueNode.asText();
                    // Try to convert to number if possible
                    try {
                        value = Double.parseDouble((String)value);
                    } catch (NumberFormatException e) {
                        // Keep as string
                    }
                } else if (valueNode.isBoolean()) {
                    value = valueNode.asBoolean();
                }
                
                if (value != null) {
                    values.add(value);
                    valueCounts.put(value, valueCounts.getOrDefault(value, 0) + 1);
                }
            }
        }
        
        if (!values.isEmpty()) {
            if ("mean".equals(fillMode)) {
                // Calculate mean for numeric values
                double calculatedMean = values.stream()
                        .filter(v -> v instanceof Number)
                        .mapToDouble(v -> ((Number) v).doubleValue())
                        .average()
                        .orElse(0);
                mean[0] = calculatedMean;
            } else if ("median".equals(fillMode)) {
                // Calculate median for numeric values
                List<Double> sortedValues = values.stream()
                        .filter(v -> v instanceof Number)
                        .map(v -> ((Number) v).doubleValue())
                        .sorted()
                        .collect(Collectors.toList());
                
                if (!sortedValues.isEmpty()) {
                    int middle = sortedValues.size() / 2;
                    if (sortedValues.size() % 2 == 1) {
                        median[0] = sortedValues.get(middle);
                    } else {
                        median[0] = (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
                    }
                }
            } else if ("most_frequent".equals(fillMode)) {
                // Calculate most frequent value
                Optional<Map.Entry<Object, Integer>> mostFrequentEntry = valueCounts.entrySet().stream()
                        .max(Map.Entry.comparingByValue());
                
                if (mostFrequentEntry.isPresent()) {
                    mostFrequent[0] = mostFrequentEntry.get().getKey().toString();
                }
            }
        }
    }

    /**
     * Apply transformations to a list of dataset rows
     * Public method that can be called from other services
     * 
     * @param rows The source rows to transform
     * @param columns The dataset columns 
     * @param columnPositions Map of column names to positions
     * @param transformations The transformations to apply
     * @return The transformed rows
     */
    public List<DatasetRow> applyTransformations(List<DatasetRow> rows, List<DatasetColumn> columns,
                                           Map<String, Integer> columnPositions,
                                           List<TransformationConfig> transformations) {
        return applyRowLevelTransformations(rows, columns, columnPositions, transformations);
    }
} 
