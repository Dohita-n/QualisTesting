package com.example.DataPreparationApp.Services.processors;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CsvFileProcessor extends BaseFileProcessor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CsvFileProcessor.class);

    public CsvFileProcessor(DatasetRepository datasetRepository,
                          DatasetColumnRepository datasetColumnRepository,
                          DatasetRowRepository datasetRowRepository,
                          ObjectMapper objectMapper) {
        super(datasetRepository, datasetColumnRepository, datasetRowRepository, objectMapper);
    }

    @Override
    public Dataset processFile(File file) throws Exception {
        log.info("Processing CSV file: {}", file.getOriginalName());
        Path filePath = Paths.get(file.getStoredPath());
        
        Dataset dataset = createDataset(file, file.getOriginalName().replace(".csv", ""));
        
        // Auto-detect delimiter (comma or semicolon)
        char delimiter = detectDelimiter(filePath);
        log.info("Detected delimiter: '{}' for file: {}", delimiter, file.getOriginalName());
        
        CSVFormat csvFormat = CSVFormat.DEFAULT
            .withDelimiter(delimiter)
            .withFirstRecordAsHeader()
            .withTrim()
            .withIgnoreEmptyLines(true);
            
        try (FileReader reader = new FileReader(filePath.toFile());
             CSVParser parser = csvFormat.parse(reader)) {
            
            Map<String, Integer> headerMap = parser.getHeaderMap();
            List<DatasetColumn> columns = new ArrayList<>();
            
            headerMap.forEach((header, position) -> 
                columns.add(createColumn(dataset, header, position))
            );
            datasetColumnRepository.saveAll(columns);
            
            List<DatasetRow> rows = new ArrayList<>();
            int rowIndex = 0;
            
            for (CSVRecord record : parser) {
                DatasetRow row = new DatasetRow();
                row.setRowNumber(rowIndex++);
                row.setDataset(dataset);
                row.setData(createRowData(columns, record.toMap()));
                rows.add(row);
                
                // Save in batches to avoid memory issues
                if (rows.size() >= 1000) {
                    saveRowsInBatches(rows);
                    rows.clear();
                }
            }
            
            // Save any remaining rows
            if (!rows.isEmpty()) {
                saveRowsInBatches(rows);
            }
            
            // Update the dataset with row and column counts
            updateDatasetCounts(dataset, columns.size(), rowIndex);
            
            return dataset;
        }
    }
    
    /**
     * Detects the delimiter used in a CSV file by examining the first line
     * Supports comma (,) and semicolon (;) delimiters
     * 
     * @param filePath Path to the CSV file
     * @return detected delimiter character
     */
    private char detectDelimiter(Path filePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(filePath), StandardCharsets.UTF_8))) {
            
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return ','; // Default to comma if file is empty
            }
            
            // Count occurrences of potential delimiters
            int commaCount = countOccurrences(firstLine, ',');
            int semicolonCount = countOccurrences(firstLine, ';');
            int tabCount = countOccurrences(firstLine, '\t');
            
            log.debug("Delimiter counts - comma: {}, semicolon: {}, tab: {}", 
                    commaCount, semicolonCount, tabCount);
            
            // Choose the most frequent delimiter
            if (semicolonCount > commaCount && semicolonCount > tabCount) {
                return ';';
            } else if (tabCount > commaCount && tabCount > semicolonCount) {
                return '\t';
            } else {
                return ','; // Default to comma
            }
        }
    }
    
    /**
     * Count occurrences of a character in a string
     */
    private int countOccurrences(String str, char c) {
        return (int) str.chars().filter(ch -> ch == c).count();
    }
}