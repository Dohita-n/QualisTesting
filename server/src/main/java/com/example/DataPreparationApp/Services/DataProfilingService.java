package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetColumnStatistics;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetColumnStatisticsRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataProfilingService {

    private final DatasetRowRepository datasetRowRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DatasetColumnStatisticsRepository datasetColumnStatisticsRepository;
    private static final int BATCH_SIZE = 1000;
    private static final int SAMPLE_SIZE = 100; // Larger sample size for better detection

    // Regex patterns for data type detection
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^[-+]?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^[-+]?\\d+\\.\\d+$");
    private static final Pattern SCIENTIFIC_PATTERN = Pattern.compile("^[-+]?(\\d+\\.?\\d*|\\.\\d+)[eE][-+]?\\d+$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[-+]?[\\$€£¥]\\s*\\d+(\\.\\d+)?$|^[-+]?\\d+(\\.\\d+)?\\s*[\\$€£¥]$");
    
    // Date formatters for direct parsing attempts
    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    public Map<String, Object> generateDatasetSummary(Dataset dataset) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", dataset.getId());
        summary.put("name", dataset.getName());
        summary.put("createdAt", dataset.getCreatedAt());
        List<DatasetColumn> columns = datasetColumnRepository.findByDataset(dataset);
        summary.put("columnCount", columns.size());
        long rowCount = datasetRowRepository.countByDataset(dataset);
        summary.put("rowCount", rowCount);
        List<Map<String, Object>> columnSummaries = columns.stream()
                .map(column -> {
                    Map<String, Object> columnSummary = new HashMap<>();
                    columnSummary.put("id", column.getId());
                    columnSummary.put("name", column.getName());
                    columnSummary.put("dataType", column.getInferredDataType());
                    if (column.getStatistics() != null) {
                        columnSummary.put("nullCount", column.getStatistics().getNullCount());
                        columnSummary.put("uniqueCount", column.getStatistics().getUniqueCount());
                    }
                    return columnSummary;
                })
                .collect(Collectors.toList());
        summary.put("columns", columnSummaries);
        return summary;
    }
    
    public Map<String, Object> generateColumnStatistics(DatasetColumn column) {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("id", column.getId());
        statistics.put("name", column.getName());
        statistics.put("dataType", column.getInferredDataType());
        if (column.getInferredDataType() == DatasetColumn.DataType.DECIMAL) {
            statistics.put("precision", column.getDecimalPrecision());
            statistics.put("scale", column.getDecimalScale());
        }
        if (column.getStatistics() != null) {
            DatasetColumnStatistics stats = column.getStatistics();
            statistics.put("nullCount", stats.getNullCount());
            statistics.put("uniqueCount", stats.getUniqueCount());
            statistics.put("min", stats.getMinValue());
            statistics.put("max", stats.getMaxValue());
            statistics.put("mean", stats.getMean());
            statistics.put("median", stats.getMedian());
            statistics.put("stdDev", stats.getStdDev());
            if (stats.getFrequentValues() != null) {
                statistics.put("frequentValues", stats.getFrequentValues());
            }
        }
        return statistics;
    }
    
    @Async
    public void analyzeDataset(Dataset dataset, List<DatasetColumn> columns) {
        log.info("Starting dataset analysis for dataset: {}", dataset.getName());
        for (DatasetColumn column : columns) {
            try {
                analyzeColumnWithSeparateTransaction(dataset, column);
            } catch (Exception e) {
                log.error("Error analyzing column {}: {}", column.getName(), e.getMessage());
            }
        }
        log.info("Dataset analysis completed for dataset: {}", dataset.getName());
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void analyzeColumnWithSeparateTransaction(Dataset dataset, DatasetColumn column) {
        try {
            analyzeColumn(dataset, column);
        } catch (Exception e) {
            log.error("Error in transaction for column {}: {}", column.getName(), e.getMessage());
            throw e;
        }
    }
    
    public void analyzeColumn(Dataset dataset, DatasetColumn column) {
        log.info("Analyzing column: {}", column.getName());
        String columnName = column.getName();
        DatasetColumnStatistics statistics = column.getStatistics();
        if (statistics == null) {
            statistics = new DatasetColumnStatistics();
            statistics.setDatasetColumn(column);
            statistics.setLastUpdated(LocalDateTime.now());
        }
        
        // Get initial sample for better data type detection
        List<String> sampleValues = getSampleValues(dataset, columnName, SAMPLE_SIZE);
        
        int pageNumber = 0;
        long nullCount = 0;
        Set<String> uniqueValues = new HashSet<>();
        Map<String, Integer> valueFrequency = new HashMap<>();
        List<Double> numericValues = new ArrayList<>();

        // Enhanced type counters
        int booleanCount = 0;
        int dateCount = 0;
        int integerCount = 0;
        int decimalCount = 0;
        int currencyCount = 0;
        int scientificCount = 0;
        int stringCount = 0;
        Set<Integer> scales = new HashSet<>();
        int maxPrecision = 0;
        int totalNonEmptyValues = 0;
        
        while (true) {
            Pageable pageable = PageRequest.of(pageNumber, BATCH_SIZE);
            Page<DatasetRow> rowPage = datasetRowRepository.findByDataset(dataset, pageable);
            if (rowPage.isEmpty()) break;
            
            for (DatasetRow row : rowPage.getContent()) {
                JsonNode data = row.getData();
                if (data.has(columnName)) {
                    JsonNode val = data.get(columnName);
                    if (val.isNull() || val.asText().trim().isEmpty() || "NULL".equalsIgnoreCase(val.asText().trim())) {
                        nullCount++;
                    } else {
                        totalNonEmptyValues++;
                        String textValue = val.asText().trim();
                        uniqueValues.add(textValue);
                        valueFrequency.put(textValue, valueFrequency.getOrDefault(textValue, 0) + 1);
                        
                        // Type analysis
                        if (isBooleanValue(textValue)) {
                            booleanCount++;
                        } else if (isDateValue(textValue)) {
                            dateCount++;
                        } else if (CURRENCY_PATTERN.matcher(textValue).matches()) {
                            currencyCount++;
                            // Extract numeric part for statistics
                            String numericPart = textValue.replaceAll("[\\$€£¥,\\s]", "");
                            try {
                                numericValues.add(Double.parseDouble(numericPart));
                                analyzeNumericPrecisionScale(numericPart, scales, maxPrecision);
                            } catch (NumberFormatException e) {
                                // Skip if not parseable
                            }
                        } else if (INTEGER_PATTERN.matcher(textValue.replaceAll("[,\\s]", "")).matches()) {
                            integerCount++;
                            try {
                                numericValues.add(Double.parseDouble(textValue.replaceAll("[,\\s]", "")));
                            } catch (NumberFormatException e) {
                                // Skip if not parseable
                            }
                        } else if (DECIMAL_PATTERN.matcher(textValue.replaceAll("[,\\s]", "")).matches()) {
                            decimalCount++;
                            try {
                                numericValues.add(Double.parseDouble(textValue.replaceAll("[,\\s]", "")));
                                // Analyze precision and scale
                                String cleanValue = textValue.replaceAll("[,\\s]", "");
                                String[] parts = cleanValue.split("\\.");
                                int scale = parts.length > 1 ? parts[1].length() : 0;
                                scales.add(scale);
                                String intPart = parts[0].replaceFirst("^[-+]", "");
                                int precision = intPart.length() + scale;
                                maxPrecision = Math.max(maxPrecision, precision);
                            } catch (NumberFormatException e) {
                                // Skip if not parseable
                            }
                        } else if (SCIENTIFIC_PATTERN.matcher(textValue).matches()) {
                            scientificCount++;
                            try {
                                numericValues.add(Double.parseDouble(textValue));
                            } catch (NumberFormatException e) {
                                // Skip if not parseable
                            }
                        } else {
                            stringCount++;
                        }
                    }
                } else {
                    nullCount++;
                }
            }
            
            if (!rowPage.hasNext()) break;
            pageNumber++;
        }
        
        // Set basic stats
        statistics.setNullCount(nullCount);
        statistics.setUniqueCount((long) uniqueValues.size());
        Map<String, Integer> topValues = valueFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
        statistics.setFrequentValues(topValues);
        
        // Infer data type with confidence scores
        inferDataTypeWithConfidence(column, totalNonEmptyValues, booleanCount, dateCount, 
                integerCount, decimalCount, currencyCount, scientificCount, 
                scales, maxPrecision);

        // Numeric summary if we have numeric values
        if (!numericValues.isEmpty()) {
            double[] vals = numericValues.stream().mapToDouble(d -> d).toArray();
            DescriptiveStatistics stats = new DescriptiveStatistics(vals);
            statistics.setMinValue(String.valueOf(stats.getMin()));
            statistics.setMaxValue(String.valueOf(stats.getMax()));
            statistics.setMean(stats.getMean());
            statistics.setMedian(stats.getPercentile(50));
            statistics.setStdDev(stats.getStandardDeviation());
        }

        column.setStatistics(statistics);
        statistics.setLastUpdated(LocalDateTime.now());
        datasetColumnRepository.saveAndFlush(column);
        log.info("Column analysis completed for: {}, inferred type: {}", column.getName(), column.getInferredDataType());
    }
    
    /**
     * Analyze numeric value for precision and scale
     */
    private void analyzeNumericPrecisionScale(String numericValue, Set<Integer> scales, int maxPrecision) {
        if (numericValue.contains(".")) {
            String[] parts = numericValue.split("\\.");
            String intPart = parts[0].replaceFirst("^[-+]", "");
            intPart = intPart.replaceAll("^0+", "");
            if (intPart.isEmpty()) intPart = "0";
            int scale = parts[1].length();
            scales.add(scale);
            int precision = intPart.length() + scale;
            maxPrecision = Math.max(maxPrecision, precision);
        }
    }

    /**
     * Use confidence scores to determine the most likely data type
     */
    private void inferDataTypeWithConfidence(DatasetColumn column, int totalNonEmptyValues,
                                            int booleanCount, int dateCount,
                                            int integerCount, int decimalCount,
                                            int currencyCount, int scientificCount,
                                            Set<Integer> scales, int maxPrecision) {
        
        if (totalNonEmptyValues == 0) {
            column.setInferredDataType(DatasetColumn.DataType.STRING);
            return;
        }
        
        double booleanConfidence = (double) booleanCount / totalNonEmptyValues;
        double dateConfidence = (double) dateCount / totalNonEmptyValues;
        double integerConfidence = (double) integerCount / totalNonEmptyValues;
        double decimalConfidence = (double) decimalCount / totalNonEmptyValues;
        double currencyConfidence = (double) currencyCount / totalNonEmptyValues;
        double scientificConfidence = (double) scientificCount / totalNonEmptyValues;
        
        // Boolean takes precedence if confidence is high enough
        if (booleanConfidence > 0.9) {
            column.setInferredDataType(DatasetColumn.DataType.BOOLEAN);
            return;
        }
        
        // Date detection with high confidence threshold
        if (dateConfidence > 0.9) {
            column.setInferredDataType(DatasetColumn.DataType.DATE);
            return;
        }
        
        // Numeric types
        double totalNumericConfidence = integerConfidence + decimalConfidence + 
                                       currencyConfidence + scientificConfidence;
                                       
        if (totalNumericConfidence > 0.8) {
            if (scientificConfidence > 0.5) {
                // Scientific notation should be treated as FLOAT
                column.setInferredDataType(DatasetColumn.DataType.FLOAT);
            } else if ((decimalConfidence + currencyConfidence) > 0.5) {
                // Determine if this should be DECIMAL or FLOAT
                boolean uniformScale = scales.size() <= 1;
                if (uniformScale && maxPrecision <= 15 && decimalConfidence > currencyConfidence) {
                    column.setInferredDataType(DatasetColumn.DataType.DECIMAL);
                    if (!scales.isEmpty()) {
                        column.setDecimalScale(scales.iterator().next());
                        column.setDecimalPrecision(maxPrecision);
                    }
                } else {
                    column.setInferredDataType(DatasetColumn.DataType.FLOAT);
                }
            } else if (integerConfidence > 0.9) {
                column.setInferredDataType(DatasetColumn.DataType.INTEGER);
            } else {
                column.setInferredDataType(DatasetColumn.DataType.FLOAT);
            }
        } else {
            column.setInferredDataType(DatasetColumn.DataType.STRING);
        }
    }

    /**
     * Get a representative sample of values from the beginning and middle of the dataset
     */
    private List<String> getSampleValues(Dataset dataset, String columnName, int sampleSize) {
        List<String> samples = new ArrayList<>();
        
        // Get samples from the beginning
        Page<DatasetRow> beginPage = datasetRowRepository.findByDataset(dataset, PageRequest.of(0, sampleSize / 2));
        extractSampleValues(beginPage.getContent(), columnName, samples);
        
        // Get samples from the middle if possible
        long count = datasetRowRepository.countByDataset(dataset);
        if (count > sampleSize) {
            int middleOffset = (int) (count / 2 - sampleSize / 4);
            if (middleOffset < 0) middleOffset = 0;
            Page<DatasetRow> middlePage = datasetRowRepository.findByDataset(dataset, 
                    PageRequest.of(middleOffset / BATCH_SIZE, Math.min(sampleSize / 2, BATCH_SIZE)));
            extractSampleValues(middlePage.getContent(), columnName, samples);
        }
        
        return samples;
    }
    
    /**
     * Extract sample values from rows
     */
    private void extractSampleValues(List<DatasetRow> rows, String columnName, List<String> samples) {
        for (DatasetRow row : rows) {
            JsonNode data = row.getData();
            if (data != null && data.has(columnName)) {
                JsonNode val = data.get(columnName);
                if (!val.isNull() && !val.asText().trim().isEmpty() && !"NULL".equalsIgnoreCase(val.asText().trim())) {
                    samples.add(val.asText().trim());
                }
            }
        }
    }

    /**
     * Check if a value is a date using various date patterns
     */
    private boolean isDateValue(String textValue) {
        if (textValue == null || textValue.trim().isEmpty()) {
            return false;
        }
        
        // Check common date patterns
        String[] commonDatePatterns = {
            "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", 
            "yyyy/MM/dd", "dd-MM-yyyy", "MM-dd-yyyy",
            "dd.MM.yyyy", "MM.dd.yyyy", "yyyy.MM.dd",
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"
        };
        
        try {
            DateUtils.parseDateStrictly(textValue, commonDatePatterns);
            return true;
        } catch (ParseException e) {
            // Try with formatters
            for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                try {
                    formatter.parse(textValue);
                    return true;
                } catch (DateTimeParseException ex) {
                    // Continue to next formatter
                }
            }
            return false;
        }
    }
    
    /**
     * Check if a value represents a boolean
     */
    private boolean isBooleanValue(String textValue) {
        textValue = textValue.trim().toLowerCase();
        return textValue.equals("true") || textValue.equals("false") ||
               textValue.equals("yes") || textValue.equals("no") ||
               textValue.equals("t") || textValue.equals("f") ||
               textValue.equals("1") || textValue.equals("0") ||
               textValue.equals("y") || textValue.equals("n");
    }
}
