package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetColumnStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetColumnStatisticsRepository extends JpaRepository<DatasetColumnStatistics, UUID> {
    Optional<DatasetColumnStatistics> findByDatasetColumn(DatasetColumn datasetColumn);
    
    /**
     * Find all statistics for a given dataset column
     * This is used to detect duplicate entries
     */
    @Query("SELECT s FROM DatasetColumnStatistics s WHERE s.datasetColumn.id = :columnId")
    List<DatasetColumnStatistics> findAllByDatasetColumnId(@Param("columnId") UUID columnId);
    
    /**
     * Delete all statistics records for a given dataset column
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM DatasetColumnStatistics s WHERE s.datasetColumn.id = :columnId")
    void deleteAllByDatasetColumnId(@Param("columnId") UUID columnId);
    
    /**
     * Check if there are duplicate statistics entries for a column
     */
    @Query("SELECT COUNT(s) > 1 FROM DatasetColumnStatistics s WHERE s.datasetColumn.id = :columnId")
    boolean hasDuplicateEntries(@Param("columnId") UUID columnId);
} 