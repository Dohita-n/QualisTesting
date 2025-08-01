package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Events.FileValidatedEvent;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.FileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileValidationService {

    private final FileRepository fileRepository;
    private final ApplicationEventPublisher eventPublisher;
    // Add a map to track files that have had events published
    private final ConcurrentHashMap<UUID, Boolean> eventPublishedFiles = new ConcurrentHashMap<>();

    @Async
    public void validateFileAsync(File file) {
        try {
            log.info("Starting schema validation for file: {}", file.getOriginalName());
            
            boolean isValid = validateFileSchema(file);
            
            // Reset the event flag for this file
            eventPublishedFiles.remove(file.getId());
            
            // Use a new transaction for updates
            updateFileStatus(file, isValid);
            
            log.info("Schema validation completed for file: {}. Result: {}", 
                     file.getOriginalName(), isValid ? "valid" : "invalid");
        } catch (Exception e) {
            // Use a new transaction for error handling
            handleValidationError(file, e);
        }
    }
    
    @Transactional
    public void updateFileStatus(File file, boolean isValid) {
        try {
            // Refresh the file entity from the database to get the latest version
            File refreshedFile = fileRepository.findById(file.getId())
                    .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + file.getId()));
            
            refreshedFile.setSchemaValid(isValid);
            if (isValid) {
                refreshedFile.setStatus(File.FileStatus.PROCESSING);
                fileRepository.save(refreshedFile);
                
                // If the file is valid, publish an event to trigger data processing
                // Only publish if we haven't already published an event for this file
                if (!eventPublishedFiles.containsKey(file.getId())) {
                    eventPublisher.publishEvent(new FileValidatedEvent(refreshedFile));
                    eventPublishedFiles.put(file.getId(), true);
                    log.info("Published file validated event for: {}", refreshedFile.getOriginalName());
                }
            } else {
                refreshedFile.setStatus(File.FileStatus.FAILED);
                fileRepository.save(refreshedFile);
            }
        } catch (Exception ex) {
            log.error("Error updating file status after validation: {}", ex.getMessage());
            // Try a simplified update if the original fails
            retryUpdateFileStatus(file, isValid);
        }
    }
    
    private void retryUpdateFileStatus(File file, boolean isValid) {
        try {
            // Use a simplified update
            File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
            refreshedFile.setSchemaValid(isValid);
            refreshedFile.setStatus(isValid ? File.FileStatus.PROCESSING : File.FileStatus.FAILED);
            fileRepository.save(refreshedFile);
            
            if (isValid) {
                // Only publish if we haven't already published an event for this file
                if (!eventPublishedFiles.containsKey(file.getId())) {
                    eventPublisher.publishEvent(new FileValidatedEvent(refreshedFile));
                    eventPublishedFiles.put(file.getId(), true);
                    log.info("Published file validated event for: {}", refreshedFile.getOriginalName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to retry update file status after validation: {}", e.getMessage());
        }
    }
    
    private boolean validateFileSchema(File file) throws IOException {
        Path filePath = Paths.get(file.getStoredPath());
        
        switch (file.getFileType()) {
            case CSV:
                return validateCsvSchema(filePath);
            case XLSX:
                return validateExcelSchema(filePath);
            // case JSON:
            //    return validateJsonSchema(filePath);
            //case SQL:
            //    return validateSQLSchema(filePath);
            default:
                log.error("Unsupported file type: {}", file.getFileType());
                return false;
        }
    }
    
    private boolean validateCsvSchema(Path filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath.toFile());
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
            
            // Validate that the CSV has at least one header
            if (parser.getHeaderMap().isEmpty()) {
                log.error("CSV file has no headers");
                return false;
            }
            
            // Check that there are data rows
            if (!parser.iterator().hasNext()) {
                log.error("CSV file has no data rows");
                return false;
            }
            
            
            return true;
        } catch (Exception e) {
            log.error("Error validating CSV schema", e);
            return false;
        }
    }
    
    private boolean validateJsonSchema(Path filePath) {
        // TODO: Implement JSON schema validation
        // For now, return true to simulate validation passing
        try {
            // Read the JSON file
            JsonNode jsonNode = new ObjectMapper().readTree(filePath.toFile());
            
            // Check if the file is a valid JSON structure
            if (jsonNode == null) {
                log.error("Invalid JSON structure");
                return false;
            }
            
            // Check if the JSON is an array with at least one element
            if (jsonNode.isArray()) {
                if (jsonNode.size() == 0) {
                    log.error("JSON array is empty");
                    return false;
                }
            } 
            // Check if it's an object with at least one field
            else if (jsonNode.isObject()) {
                if (jsonNode.size() == 0) {
                    log.error("JSON object has no fields");
                    return false;
                }
            } else {
                log.error("JSON is neither an object nor an array");
                return false;
            }
            
            // Additional validation could be added here, such as:
            // - Required fields
            // - Data type validation for specific fields
            // - Nested structure validation
            
            return true;
        } catch (Exception e) {
            log.error("Error validating JSON schema", e);
            return false;
        }
        //return true;
    }
    
    private boolean validateExcelSchema(Path filePath) {
        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            log.info("Validating Excel file: {}", filePath.getFileName());
            
            // Check if workbook has at least one sheet
            if (workbook.getNumberOfSheets() == 0) {
                log.error("Excel file has no sheets");
                return false;
            }
            
            log.info("Excel file has {} sheets", workbook.getNumberOfSheets());
            
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                log.error("First sheet is null");
                return false;
            }
            
            log.info("Processing sheet: {} with {} rows", sheet.getSheetName(), sheet.getLastRowNum() + 1);
            
            // Find the first non-empty row as header
            Row headerRow = null;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && !isEmptyRow(row)) {
                    headerRow = row;
                    log.info("Found header row at index: {}", i);
                    break;
                }
            }
            
            if (headerRow == null) {
                log.error("No header row found in Excel file");
                return false;
            }
            
            // Check that header row has at least one non-empty column
            boolean hasValidHeader = false;
            int validColumns = 0;
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    try {
                        String cellValue = cell.getStringCellValue();
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            hasValidHeader = true;
                            validColumns++;
                        }
                    } catch (Exception e) {
                        log.warn("Error reading header cell at position {}: {}", i, e.getMessage());
                    }
                }
            }
            
            log.info("Found {} valid columns in header", validColumns);
            
            if (!hasValidHeader) {
                log.error("No valid headers found in Excel file");
                return false;
            }
            
            // Check that there are data rows
            boolean hasDataRows = false;
            int dataRowCount = 0;
            for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && !isEmptyRow(row)) {
                    hasDataRows = true;
                    dataRowCount++;
                }
            }
            
            log.info("Found {} data rows", dataRowCount);
            
            if (!hasDataRows) {
                log.error("Excel file has no data rows");
                return false;
            }
            
            log.info("Excel file validation successful");
            return true;
            
        } catch (Exception e) {
            log.error("Error validating Excel schema: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                try {
                    switch (cell.getCellType()) {
                        case STRING:
                            if (cell.getStringCellValue() != null && !cell.getStringCellValue().trim().isEmpty()) {
                                return false;
                            }
                            break;
                        case NUMERIC:
                            return false; // Numeric values are considered non-empty
                        case BOOLEAN:
                            return false; // Boolean values are considered non-empty
                        case FORMULA:
                            return false; // Formula cells are considered non-empty
                        case BLANK:
                            break; // Blank cells are considered empty
                        case ERROR:
                            break; // Error cells are considered empty
                        default:
                            break;
                    }
                } catch (Exception e) {
                    log.warn("Error reading cell type: {}", e.getMessage());
                    // If we can't read the cell, consider it non-empty to be safe
                    return false;
                }
            }
        }
        return true;
    }
    

    @Transactional
    public void handleValidationError(File file, Exception e) {
        try {
            log.error("Error during schema validation for file: {}", file.getOriginalName(), e);
            
            // Refresh the entity from database
            File refreshedFile = fileRepository.findById(file.getId())
                    .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + file.getId()));
            
            refreshedFile.setSchemaValid(false);
            refreshedFile.setStatus(File.FileStatus.FAILED);
            fileRepository.save(refreshedFile);
        } catch (Exception ex) {
            
            // Try a simplified update if the original fails
            try {
                File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
                refreshedFile.setSchemaValid(false);
                refreshedFile.setStatus(File.FileStatus.FAILED);
                fileRepository.save(refreshedFile);
            } catch (Exception e2) {
                log.error("Failed to retry update file status after validation error: {}", e2.getMessage());
            }
        }
    }
} 