package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Preparation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.Optional;


import java.util.List;
import java.util.UUID;

@Repository
public interface PreparationRepository extends JpaRepository<Preparation, UUID> {
    List<Preparation> findByCreatedById(UUID userId);
    List<Preparation> findBySourceDatasetId(UUID datasetId);
    @Query("SELECT p FROM Preparation p LEFT JOIN FETCH p.transformationSteps WHERE p.id = :id")
    Optional<Preparation> findByIdWithTransformationSteps(@PathVariable UUID id);
} 