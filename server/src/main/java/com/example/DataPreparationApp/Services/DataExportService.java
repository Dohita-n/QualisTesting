package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportService {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DatasetRowRepository datasetRowRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.file-storage.root-dir:./uploads}")
    private String rootDirectory;

    /**
     * Export a dataset to CSV format
     *
     * @param datasetId The ID of the dataset to export
     * @return The path to the exported CSV file
     * @throws IOException If an error occurs during export
     */
    public String exportDatasetToCsv(UUID datasetId) throws IOException {
        // Get the dataset
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        // Get all columns
        List<DatasetColumn> columns = datasetColumnRepository.findByDatasetIdOrderByPosition(datasetId);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("No columns found for dataset: " + datasetId);
        }

        // Get all rows
        List<DatasetRow> rows = datasetRowRepository.findByDatasetIdOrderByRowNumber(datasetId);
        if (rows.isEmpty()) {
            log.warn("No rows found for dataset: {}. Attempting native query.", datasetId);
            rows = datasetRowRepository.findByDatasetIdNative(datasetId, Integer.MAX_VALUE);
        }

        // Create export directory if it doesn't exist
        String exportDirPath = rootDirectory + "/exports";
        Path exportDir = Paths.get(exportDirPath);
        Files.createDirectories(exportDir);

        // Generate unique filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sanitizedName = dataset.getName().replaceAll("[^a-zA-Z0-9]", "_");
        String filename = sanitizedName + "_" + timestamp + ".csv";
        Path csvFilePath = exportDir.resolve(filename);

        // Create headers from column names
        String[] headers = columns.stream()
                .map(DatasetColumn::getName)
                .toArray(String[]::new);

        // Write data to CSV file using Excel-compatible format
        try (BufferedWriter writer = Files.newBufferedWriter(csvFilePath, 
                java.nio.charset.StandardCharsets.UTF_8)) {
            
            // Write UTF-8 BOM for Excel compatibility
            writer.write('\ufeff');
            
            // Create CSV printer with auto-closing
            try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.EXCEL
                .withHeader(headers)
                .withDelimiter(',')
                .withQuoteMode(org.apache.commons.csv.QuoteMode.MINIMAL))) {
                
                // Write each row to the CSV
                for (DatasetRow row : rows) {
                    List<String> rowValues = new ArrayList<>();
                    JsonNode data = row.getData();

                    // For each column, extract the value from the row data
                    for (DatasetColumn column : columns) {
                        String columnName = column.getName();
                        JsonNode value = data.get(columnName);
                        
                        // Handle null values and convert JsonNode to string
                        if (value == null || value.isNull()) {
                            rowValues.add("");
                        } else if (value.isTextual()) {
                            rowValues.add(value.asText());
                        } else {
                            rowValues.add(value.toString());
                        }
                    }

                    // Print the row to CSV
                    csvPrinter.printRecord(rowValues);
                }
            }
        }

        log.info("Dataset exported to CSV: {}", csvFilePath);
        return "/exports/" + filename;
    }

    /**
     * Export a filtered dataset based on applied transformations
     * 
     * @param datasetId The ID of the dataset to export
     * @param rows The pre-filtered rows to export
     * @param columns The columns to include in the export
     * @return The path to the exported CSV file
     * @throws IOException If an error occurs during export
     */
    public String exportFilteredDatasetToCsv(UUID datasetId, List<DatasetRow> rows, List<DatasetColumn> columns) 
            throws IOException {
        // Get the dataset
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        // Create export directory if it doesn't exist
        String exportDirPath = rootDirectory + "/exports";
        Path exportDir = Paths.get(exportDirPath);
        Files.createDirectories(exportDir);

        // Generate unique filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sanitizedName = dataset.getName().replaceAll("[^a-zA-Z0-9]", "_");
        String filename = sanitizedName + "_filtered_" + timestamp + ".csv";
        Path csvFilePath = exportDir.resolve(filename);

        // Create headers from column names
        String[] headers = columns.stream()
                .map(DatasetColumn::getName)
                .toArray(String[]::new);

        // Write data to CSV file using Excel-compatible format
        try (BufferedWriter writer = Files.newBufferedWriter(csvFilePath, 
                java.nio.charset.StandardCharsets.UTF_8)) {
            
            // Write UTF-8 BOM for Excel compatibility
            writer.write('\ufeff');
            
            // Create CSV printer with auto-closing
            try (CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.EXCEL
                .withHeader(headers)
                .withDelimiter(',')
                .withQuoteMode(org.apache.commons.csv.QuoteMode.MINIMAL))) {
                
                // Write each row to the CSV
                for (DatasetRow row : rows) {
                    List<String> rowValues = new ArrayList<>();
                    JsonNode data = row.getData();

                    // For each column, extract the value from the row data
                    for (DatasetColumn column : columns) {
                        String columnName = column.getName();
                        JsonNode value = data.get(columnName);
                        
                        // Handle null values and convert JsonNode to string
                        if (value == null || value.isNull()) {
                            rowValues.add("");
                        } else if (value.isTextual()) {
                            rowValues.add(value.asText());
                        } else {
                            rowValues.add(value.toString());
                        }
                    }

                    // Print the row to CSV
                    csvPrinter.printRecord(rowValues);
                }
            }
        }

        log.info("Filtered dataset exported to CSV: {}", csvFilePath);
        return "/exports/" + filename;
    }
} 