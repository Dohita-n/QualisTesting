package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetColumnStatistics;
import com.example.DataPreparationApp.Repository.DatasetColumnStatisticsRepository;
//import org.hibernate.NonUniqueResultException;
import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class ValidationService {

    private static final Logger logger = Logger.getLogger(ValidationService.class.getName());
    private final JdbcTemplate jdbcTemplate;
    private final DatasetColumnStatisticsRepository statisticsRepository;
    
    // Cache for column validation results to reduce database load
    private final Map<UUID, Map<String, Object>> validationCache = new HashMap<>();
    // Cache expiration control
    private final Map<UUID, LocalDateTime> validationCacheTimestamps = new HashMap<>();
    private static final long CACHE_TTL_MINUTES = 15; // Cache expiry time in minutes
    
    @Autowired
    public ValidationService(
            JdbcTemplate jdbcTemplate,
            DatasetColumnStatisticsRepository statisticsRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.statisticsRepository = statisticsRepository;
    }

    /**
     * Get validation information for a column. If validation info doesn't exist,
     * apply a default validation pattern based on the column's data type.
     * 
     * @param column The dataset column to get validation info for
     * @return Map containing validation results
     */
    @Transactional
    public Map<String, Object> getColumnValidationInfo(DatasetColumn column) {
        try {
            // Try to get statistics safely (handles potential duplicates)
            DatasetColumnStatistics stats = getOrCreateStatistics(column);
            
            // Check if validation information exists
            if (stats.getValidationPattern() == null || 
                stats.getValidCount() == null || 
                stats.getInvalidCount() == null) {
                
                // Apply default validation pattern based on data type
                String defaultPattern = getDefaultValidationPattern(column.getInferredDataType());
                
                // Run validation with default pattern
                return validateColumn(column, defaultPattern);
            }
            
            // Return existing validation info
            Map<String, Object> result = new HashMap<>();
            result.put("column", column.getName());
            result.put("validCount", stats.getValidCount());
            result.put("invalidCount", stats.getInvalidCount());
            result.put("nullCount", stats.getNullCount());
            result.put("totalCount", stats.getValidCount() + stats.getInvalidCount() + stats.getNullCount());
            result.put("pattern", stats.getValidationPattern());
            result.put("lastValidated", stats.getLastValidated());
            
            return result;
        } catch (Exception e) {
            logger.warning("Error getting validation info: " + e.getMessage());
            
            // Try to repair the database
            cleanupDuplicateStatistics(column);
            
            // Return empty results for now
            Map<String, Object> result = new HashMap<>();
            result.put("column", column.getName());
            result.put("validCount", 0);
            result.put("invalidCount", 0);
            result.put("nullCount", 0);
            result.put("totalCount", 0);
            result.put("pattern", "");
            
            return result;
        }
    }
    
    /**
     * Safe method to get or create DatasetColumnStatistics
     * Handles potential duplicate records using direct SQL
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DatasetColumnStatistics getOrCreateStatistics(DatasetColumn column) {
        try {
            // First check if there are any statistics for this column - use SQL directly to avoid Hibernate caching issues
            String countQuery = "SELECT COUNT(*) FROM dataset_column_statistics WHERE dataset_column_id = ?";
            Integer count = jdbcTemplate.queryForObject(countQuery, Integer.class, column.getId());
            
            if (count != null && count > 1) {
                // There are duplicates, clean them up before proceeding
                fixDuplicatesWithSql(column.getId());
            }
            
            // Try to get the statistics directly with findByDatasetColumn to avoid ID issues
            return statisticsRepository.findByDatasetColumn(column)
                    .orElse(createNewStatistics(column));
        } catch (Exception e) {
            logger.severe("Error in getOrCreateStatistics: " + e.getMessage());
            // If anything goes wrong, try to clean up and create a new record
            try {
                fixDuplicatesWithSql(column.getId());
                // Create a new statistics record after cleanup
                DatasetColumnStatistics stats = createNewStatistics(column);
                // Save manually bypassing cache issues
                saveStatisticsSafely(stats);
                return stats;
            } catch (Exception ex) {
                logger.severe("Failed to recover from duplicate statistics error: " + ex.getMessage());
                // Return a temporary in-memory object as last resort
                DatasetColumnStatistics stats = new DatasetColumnStatistics();
                stats.setDatasetColumn(column);
                stats.setLastUpdated(LocalDateTime.now());
                return stats;
            }
        }
    }
    
    /**
     * Create a new statistics record for a column
     */
    private DatasetColumnStatistics createNewStatistics(DatasetColumn column) {
        DatasetColumnStatistics stats = new DatasetColumnStatistics();
        stats.setDatasetColumn(column);
        stats.setLastUpdated(LocalDateTime.now());
        return stats;
    }
    
    /**
     * Fix duplicate statistics using direct SQL to bypass Hibernate cache issues
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fixDuplicatesWithSql(UUID columnId) {
        logger.warning("Fixing duplicate statistics for column ID: " + columnId);
        
        try {
            // Find all IDs for this column
            String findIdsQuery = "SELECT id FROM dataset_column_statistics WHERE dataset_column_id = ? ORDER BY last_updated DESC";
            List<String> ids = jdbcTemplate.queryForList(findIdsQuery, String.class, columnId);
            
            if (ids.size() <= 1) {
                logger.info("No duplicates found for column ID: " + columnId);
                return;
            }
            
            // Keep the first (newest) and delete the rest
            String latestId = ids.get(0);
            
            for (int i = 1; i < ids.size(); i++) {
                String deleteQuery = "DELETE FROM dataset_column_statistics WHERE id = ?";
                jdbcTemplate.update(deleteQuery, ids.get(i));
            }
            
            logger.info("Successfully fixed duplicates for column ID: " + columnId + ". Kept ID: " + latestId);
        } catch (Exception e) {
            logger.severe("Error fixing duplicates with SQL: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Save statistics safely, bypassing potential Hibernate cache issues
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveStatisticsSafely(DatasetColumnStatistics stats) {
        try {
            // Check if this column already has statistics
            String countQuery = "SELECT COUNT(*) FROM dataset_column_statistics WHERE dataset_column_id = ?";
            Integer count = jdbcTemplate.queryForObject(countQuery, Integer.class, stats.getDatasetColumn().getId());
            
            if (count != null && count > 0) {
                // Update existing record
                String updateQuery = "UPDATE dataset_column_statistics SET " + 
                    "validation_pattern = ?, " + 
                    "valid_count = ?, " + 
                    "invalid_count = ?, " + 
                    "null_count = ?, " + 
                    "last_validated = ?, " + 
                    "last_updated = ? " + 
                    "WHERE dataset_column_id = ?";
                
                jdbcTemplate.update(updateQuery, 
                    stats.getValidationPattern(),
                    stats.getValidCount(), 
                    stats.getInvalidCount(),
                    stats.getNullCount(),
                    stats.getLastValidated(),
                    LocalDateTime.now(),
                    stats.getDatasetColumn().getId()
                );
            } else {
                // Insert new record
                String insertQuery = "INSERT INTO dataset_column_statistics " + 
                    "(id, dataset_column_id, validation_pattern, valid_count, invalid_count, null_count, last_validated, last_updated, created_date) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                
                jdbcTemplate.update(insertQuery,
                    UUID.randomUUID().toString(),
                    stats.getDatasetColumn().getId().toString(),
                    stats.getValidationPattern(),
                    stats.getValidCount() != null ? stats.getValidCount() : 0,
                    stats.getInvalidCount() != null ? stats.getInvalidCount() : 0,
                    stats.getNullCount() != null ? stats.getNullCount() : 0,
                    stats.getLastValidated() != null ? stats.getLastValidated() : LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now()
                );
            }
        } catch (Exception e) {
            logger.severe("Error in saveStatisticsSafely: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Clean up duplicate statistics records
     * Delete all existing records and create a fresh one
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanupDuplicateStatistics(DatasetColumn column) {
        logger.warning("Cleaning up duplicate statistics for column ID: " + column.getId());
        
        try {
            // Use direct SQL for maximum reliability
            fixDuplicatesWithSql(column.getId());
            logger.info("Successfully cleaned up duplicate statistics for column ID: " + column.getId());
        } catch (Exception e) {
            logger.severe("Error cleaning up duplicate statistics: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Get default validation pattern based on data type
     * 
     * @param dataType The column's data type
     * @return A default regex pattern for validation
     */
    private String getDefaultValidationPattern(DatasetColumn.DataType dataType) {
        if (dataType == null) {
            return "^.+$"; // Default for any non-empty string
        }
        
        switch (dataType) {
            case INTEGER:
                return "^[-+]?\\d+$";
            case FLOAT:
            case DECIMAL:
                return "^[-+]?\\d*\\.?\\d+$";
            case DATE:
                return "^(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})$";
            case BOOLEAN:
                return "^(true|false|yes|f|t|no|0|1)$";
            case STRING:
            default:
                return "^.+$";
        }
    }

    @Transactional
    public Map<String, Object> validateColumn(DatasetColumn column, String patternStr) {
        // Check cache first if this is a recent validation request
        UUID columnId = column.getId();
        if (validationCache.containsKey(columnId) && 
            validationCacheTimestamps.containsKey(columnId)) {
            
            LocalDateTime cacheTime = validationCacheTimestamps.get(columnId);
            if (cacheTime.plusMinutes(CACHE_TTL_MINUTES).isAfter(LocalDateTime.now())) {
                Map<String, Object> cachedResult = validationCache.get(columnId);
                // Return cached result only if pattern matches
                if (patternStr.equals(cachedResult.get("pattern"))) {
                    logger.info("Using cached validation results for column: " + column.getName());
                    return cachedResult;
                }
            }
        }

        // Validate the regex pattern
        try {
            Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex pattern: " + e.getMessage());
        }

        // Convert JavaScript-style regex to PostgreSQL compatible regex
        String pgPattern = convertToPostgresRegex(patternStr);

        // Data is stored in the dataset_rows table in JSONB format
        String columnName = column.getName();
        String datasetId = column.getDataset().getId().toString();
        
        // Count valid entries (matching the regex pattern)
        // Use ~* for case-insensitive matching to be consistent with JavaScript RegExp('pattern', 'i')
        String validCountQuery = 
            "SELECT COUNT(*) FROM dataset_rows " +
            "WHERE dataset_id::text = ? " +
            "AND data->>? ~* ? " +
            "AND data->>? IS NOT NULL " +
            "AND data->>? != ''";
        Long validCount = jdbcTemplate.queryForObject(
            validCountQuery, 
            Long.class, 
            datasetId, 
            columnName, 
            pgPattern, 
            columnName,
            columnName
        );
        
        // Count invalid entries (not matching the regex pattern, but not null)
        String invalidCountQuery = 
            "SELECT COUNT(*) FROM dataset_rows " +
            "WHERE dataset_id::text = ? " +
            "AND data->>? !~* ? " +
            "AND data->>? IS NOT NULL " +
            "AND data->>? != ''";
        Long invalidCount = jdbcTemplate.queryForObject(
            invalidCountQuery, 
            Long.class, 
            datasetId, 
            columnName, 
            pgPattern, 
            columnName,
            columnName
        );
        
        // Count null entries
        String nullCountQuery = 
            "SELECT COUNT(*) FROM dataset_rows " +
            "WHERE dataset_id::text = ? " +
            "AND (data->>? IS NULL OR data->>? = '' OR TRIM(data->>?) = '')";
        Long nullCount = jdbcTemplate.queryForObject(
            nullCountQuery, 
            Long.class, 
            datasetId, 
            columnName, 
            columnName,
            columnName
        );
        
        try {
            // Handle potential duplicates first
            fixDuplicatesWithSql(column.getId());
            
            // Get or create statistics
            DatasetColumnStatistics stats = getOrCreateStatistics(column);
            
            // Update validation fields
            stats.setValidationPattern(patternStr);
            stats.setValidCount(validCount);
            stats.setInvalidCount(invalidCount);
            stats.setNullCount(nullCount);
            stats.setLastValidated(LocalDateTime.now());
            stats.setLastUpdated(LocalDateTime.now());
            
            // Save safely using direct SQL to bypass Hibernate caching issues
            saveStatisticsSafely(stats);
        } catch (Exception e) {
            logger.warning("Error updating statistics (continuing anyway): " + e.getMessage());
            // Continue to return results even if saving fails
        }
        
        // Return the validation results
        Map<String, Object> result = new HashMap<>();
        result.put("column", column.getName());
        result.put("validCount", validCount);
        result.put("invalidCount", invalidCount);
        result.put("nullCount", nullCount);
        result.put("totalCount", validCount + invalidCount + nullCount);
        result.put("pattern", patternStr);
        result.put("datasetId", column.getDataset().getId());
        
        // Store in cache
        validationCache.put(columnId, new HashMap<>(result));
        validationCacheTimestamps.put(columnId, LocalDateTime.now());
        
        return result;
    }
    
    /**
     * Convert JavaScript regex pattern to PostgreSQL compatible regex
     * Handles special cases like word boundaries (\b) which work differently
     * in JavaScript vs PostgreSQL
     */
    private String convertToPostgresRegex(String jsRegex) {
        String pgRegex = jsRegex;
        
        // Handle JavaScript word boundaries \b
        // PostgreSQL word boundaries are different: \y or [[:<:]] and [[:>:]]
        if (pgRegex.contains("\\b")) {
            // Replace JavaScript word boundaries with PostgreSQL word boundaries
            // This is a simplified approach - complex cases may need more specific handling
            pgRegex = pgRegex.replace("\\b", "\\y");
            
            // Special case for patterns like \b\d{2}\.\d{2}\b which match numbers like 12.34
            if (pgRegex.matches("\\\\y\\\\d\\{2\\}\\.\\\\d\\{2\\}\\\\y")) {
                // Enhance it to better match across engines
                logger.info("Special case handling for decimal number pattern with word boundaries");
                pgRegex = "(^|[^0-9])\\d{2}\\.\\d{2}($|[^0-9])";
            }
        }
        
        // Log if we modified the pattern
        if (!pgRegex.equals(jsRegex)) {
            logger.info("Converted JS regex '" + jsRegex + "' to PG regex '" + pgRegex + "'");
        }
        
        return pgRegex;
    }

    /**
     * Clear the validation cache for a specific column
     */
    public void clearValidationCache(UUID columnId) {
        validationCache.remove(columnId);
        validationCacheTimestamps.remove(columnId);
        logger.info("Cleared validation cache for column ID: " + columnId);
    }

    /**
     * Clear validation cache for all columns in a dataset
     */
    public void clearDatasetValidationCache(UUID datasetId) {
        List<UUID> keysToRemove = validationCache.keySet().stream()
            .filter(columnId -> {
                Map<String, Object> result = validationCache.get(columnId);
                if (result != null && result.containsKey("datasetId")) {
                    return datasetId.equals(result.get("datasetId"));
                }
                return false;
            })
            .collect(Collectors.toList());
        
        keysToRemove.forEach(key -> {
            validationCache.remove(key);
            validationCacheTimestamps.remove(key);
        });
        
        logger.info("Cleared validation cache for dataset ID: " + datasetId);
    }
} 