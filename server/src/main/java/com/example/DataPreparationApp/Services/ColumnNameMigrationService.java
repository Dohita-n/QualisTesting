package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service to migrate existing column names to normalized format (trim + toLowerCase)
 * This ensures consistency across the application
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ColumnNameMigrationService {

    private final DatasetColumnRepository datasetColumnRepository;

    /**
     * Normalize column name by trimming whitespace and converting to lowercase
     * 
     * @param name The original column name
     * @return The normalized column name
     */
    private String normalizeColumnName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase();
    }

    /**
     * Migrate all existing column names to normalized format
     * This should be run once to ensure consistency
     */
    @Transactional
    public void migrateColumnNames() {
        log.info("Starting column name migration...");
        
        List<DatasetColumn> allColumns = datasetColumnRepository.findAll();
        int migratedCount = 0;
        
        for (DatasetColumn column : allColumns) {
            String originalName = column.getName();
            String normalizedName = normalizeColumnName(originalName);
            
            if (!originalName.equals(normalizedName)) {
                log.info("Migrating column name: '{}' -> '{}'", originalName, normalizedName);
                column.setName(normalizedName);
                datasetColumnRepository.save(column);
                migratedCount++;
            }
        }
        
        log.info("Column name migration completed. {} columns migrated.", migratedCount);
    }

    /**
     * Check if any column names need migration
     * 
     * @return true if migration is needed, false otherwise
     */
    public boolean needsMigration() {
        List<DatasetColumn> allColumns = datasetColumnRepository.findAll();
        
        for (DatasetColumn column : allColumns) {
            String originalName = column.getName();
            String normalizedName = normalizeColumnName(originalName);
            
            if (!originalName.equals(normalizedName)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Get a report of columns that need migration
     * 
     * @return List of column names that need migration
     */
    public List<String> getColumnsNeedingMigration() {
        List<DatasetColumn> allColumns = datasetColumnRepository.findAll();
        
        return allColumns.stream()
                .filter(column -> {
                    String originalName = column.getName();
                    String normalizedName = normalizeColumnName(originalName);
                    return !originalName.equals(normalizedName);
                })
                .map(column -> String.format("'%s' -> '%s'", 
                    column.getName(), 
                    normalizeColumnName(column.getName())))
                .toList();
    }
} 