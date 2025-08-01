package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DatasetColumnRepository extends JpaRepository<DatasetColumn, UUID> {
    List<DatasetColumn> findByDataset(Dataset dataset);
    List<DatasetColumn> findByDatasetOrderByPosition(Dataset dataset);
    List<DatasetColumn> findByDatasetIdOrderByPosition(UUID datasetId);
    
    List<DatasetColumn> findByName(String name);
    
    @Modifying
    @Transactional
    void deleteByDataset(Dataset dataset);
} 