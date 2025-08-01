package com.example.DataPreparationApp.Model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dataset_columns")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DatasetColumn {

    @Id
    @GeneratedValue(generator ="UUID")
    @GenericGenerator(name="UUID",strategy="org.hibernate.id.UUIDGenerator")
    @Column(name="id",updatable = false,nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    @JsonBackReference(value="dataset-columns")
    private Dataset dataset;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "inferred_data_type")
    private DataType inferredDataType;

    @Column(name = "is_nullable")
    private Boolean isNullable;

    private Boolean uniqueness;

    private String format;
    
    @Column(name = "decimal_precision")
    private Integer decimalPrecision;
    
    @Column(name = "decimal_scale")
    private Integer decimalScale;

    @OneToOne(mappedBy = "datasetColumn", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("datasetColumn")
    private DatasetColumnStatistics statistics;

    public enum DataType {
        STRING, INTEGER, FLOAT, DATE, BOOLEAN, DECIMAL
    }
}