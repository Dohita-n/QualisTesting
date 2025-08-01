package com.example.DataPreparationApp.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "dataset_column_statistics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DatasetColumnStatistics {

    @Id
    @GeneratedValue(generator ="UUID")
    @GenericGenerator(name="UUID",strategy="org.hibernate.id.UUIDGenerator")
    @Column(name="id",updatable = false,nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_column_id", nullable = false)
    @JsonIgnoreProperties("statistics")
    private DatasetColumn datasetColumn;

    @Column(name = "null_count")
    private Long nullCount;

    @Column(name = "unique_count")
    private Long uniqueCount;

    @Column(name = "min_value")
    private String minValue;

    @Column(name = "max_value")
    private String maxValue;

    @Column(name = "mean")
    private Double mean;

    @Column(name = "median")
    private Double median;

    @Column(name = "std_dev")
    private Double stdDev;
    
    @Type(JsonBinaryType.class)
    @Column(name = "frequent_values", columnDefinition = "jsonb")
    private Map<String, Integer> frequentValues;

    @Column(name = "validation_pattern")
    private String validationPattern;
    
    @Column(name = "valid_count")
    private Long validCount;
    
    @Column(name = "invalid_count")
    private Long invalidCount;
    
    @Column(name = "last_validated")
    private LocalDateTime lastValidated;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}