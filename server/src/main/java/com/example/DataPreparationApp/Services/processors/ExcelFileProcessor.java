package com.example.DataPreparationApp.Services.processors;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExcelFileProcessor extends BaseFileProcessor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExcelFileProcessor.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public ExcelFileProcessor(DatasetRepository datasetRepository,
                            DatasetColumnRepository datasetColumnRepository,
                            DatasetRowRepository datasetRowRepository,
                            ObjectMapper objectMapper) {
        super(datasetRepository, datasetColumnRepository, datasetRowRepository, objectMapper);
    }

    @Override
    public Dataset processFile(File file) throws Exception {
        log.info("Processing Excel file: {}", file.getOriginalName());
        Dataset dataset = createDataset(file, cleanFileName(file.getOriginalName()));
        Path filePath = Paths.get(file.getStoredPath());
        
        log.info("File path: {}", filePath);
        log.info("File exists: {}", filePath.toFile().exists());
        
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            log.info("Workbook created successfully, number of sheets: {}", workbook.getNumberOfSheets());
            
            // Get the first sheet
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel file contains no sheets");
            }
            
            log.info("Processing sheet: {} with {} rows", sheet.getSheetName(), sheet.getLastRowNum() + 1);
            
            // Find the first non-empty row as header
            Row headerRow = findFirstNonEmptyRow(sheet);
            if (headerRow == null) {
                throw new IllegalArgumentException("No header row found in Excel file");
            }
            
            log.info("Found header row at index: {}", headerRow.getRowNum());
            
            // Create columns from header row
            List<DatasetColumn> columns = new ArrayList<>();
            int maxColumnIndex = headerRow.getLastCellNum();
            
            log.info("Processing {} columns in header row", maxColumnIndex);
            
            for (int i = 0; i < maxColumnIndex; i++) {
                Cell cell = headerRow.getCell(i);
                String columnName = getCellValueAsString(cell);
                if (columnName != null && !columnName.trim().isEmpty()) {
                    columns.add(createColumn(dataset, columnName.trim(), i));
                    log.debug("Created column: {} at position {}", columnName.trim(), i);
                }
            }
            
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("No valid columns found in header row");
            }
            
            datasetColumnRepository.saveAll(columns);
            log.info("Created {} columns for dataset", columns.size());
            
            // Process data rows
            List<DatasetRow> rows = new ArrayList<>();
            int rowCount = 0;
            int lastRowNum = sheet.getLastRowNum();
            
            log.info("Processing data rows from {} to {}", headerRow.getRowNum() + 1, lastRowNum);
            
            for (int i = headerRow.getRowNum() + 1; i <= lastRowNum; i++) {
                Row currentRow = sheet.getRow(i);
                if (currentRow == null || isEmptyRow(currentRow)) {
                    log.debug("Skipping empty row at index: {}", i);
                    continue; // Skip empty rows
                }
                
                DatasetRow row = new DatasetRow();
                row.setRowNumber(rowCount + 1); // Use 1-based indexing for display
                row.setDataset(dataset);
                
                Map<String, String> rowData = new HashMap<>();
                boolean hasData = false;
                
                for (DatasetColumn column : columns) {
                    Cell cell = currentRow.getCell(column.getPosition());
                    String cellValue = getCellValueAsString(cell);
                    rowData.put(column.getName(), cellValue);
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        hasData = true;
                    }
                }
                
                if (hasData) {
                    row.setData(createRowData(columns, rowData));
                    rows.add(row);
                    rowCount++;
                    
                    // Save in batches to avoid memory issues
                    if (rows.size() >= 1000) {
                        saveRowsInBatches(rows);
                        rows.clear();
                        log.info("Processed {} rows so far", rowCount);
                    }
                }
            }
            
            // Save any remaining rows
            if (!rows.isEmpty()) {
                saveRowsInBatches(rows);
            }
            
            // Update the dataset with row and column counts
            updateDatasetCounts(dataset, columns.size(), rowCount);
            
            log.info("Excel file processing completed successfully: {}", file.getOriginalName());
            return dataset;
            
        } catch (Exception e) {
            log.error("Error processing Excel file: {}", file.getOriginalName(), e);
            throw new Exception("Failed to process Excel file: " + e.getMessage(), e);
        }
    }
    
    private Row findFirstNonEmptyRow(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isEmptyRow(row)) {
                return row;
            }
        }
        return null;
    }
    
    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        int lastCellNum = row.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                String cellValue = getCellValueAsString(cell);
                if (cellValue != null && !cellValue.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
    
    private String cleanFileName(String fileName) {
        return fileName.replaceAll("(?i)\\.xlsx?$", "");
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        try {
            CellType cellType = cell.getCellType();
            
            switch (cellType) {
                case STRING:
                    String stringValue = cell.getStringCellValue();
                    return stringValue != null ? stringValue : "";
                    
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        try {
                            return DATE_FORMAT.format(cell.getDateCellValue());
                        } catch (Exception e) {
                            log.warn("Error formatting date cell: {}", e.getMessage());
                            return String.valueOf(cell.getNumericCellValue());
                        }
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        // Check if it's an integer
                        if (numericValue == (long) numericValue) {
                            return String.valueOf((long) numericValue);
                        } else {
                            return String.valueOf(numericValue);
                        }
                    }
                    
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                    
                case FORMULA:
                    try {
                        // Try to get the formula result as numeric
                        double numericResult = cell.getNumericCellValue();
                        if (numericResult == (long) numericResult) {
                            return String.valueOf((long) numericResult);
                        } else {
                            return String.valueOf(numericResult);
                        }
                    } catch (Exception e) {
                        try {
                            // Try to get the formula result as string
                            return cell.getStringCellValue();
                        } catch (Exception e2) {
                            log.warn("Error reading formula cell: {}", e2.getMessage());
                            return "";
                        }
                    }
                    
                case BLANK:
                    return "";
                    
                case ERROR:
                    return "";
                    
                default:
                    return "";
            }
        } catch (Exception e) {
            log.warn("Error reading cell value: {}", e.getMessage());
            return "";
        }
    }
}