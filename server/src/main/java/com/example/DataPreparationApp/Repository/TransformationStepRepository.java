package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.TransformationStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransformationStepRepository extends JpaRepository<TransformationStep, UUID> {
    
    /**
     * Find the most recent transformation step for a dataset
     */
    Optional<TransformationStep> findFirstByDatasetOrderByAppliedAtDesc(Dataset dataset);
} 