package com.example.DataPreparationApp.Model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "datasets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Dataset {

    @Id
    @GeneratedValue(generator ="UUID")
    @GenericGenerator(name="UUID",strategy="org.hibernate.id.UUIDGenerator")
    @Column(name="id",updatable = false,nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    @JsonIgnoreProperties({"datasets", "jobMetadata"})
    private File file;

    @Column(nullable = false)
    private String name;

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;
    
    @Column(name = "row_count", nullable = false)
    @Builder.Default
    private Long rowCount = 0L;
    
    @Column(name = "column_count", nullable = false)
    @Builder.Default
    private Integer columnCount = 0;

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL)
    @JsonManagedReference(value="dataset-columns")
    @Builder.Default
    private List<DatasetColumn> columns = new ArrayList<>();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("dataset")
    @Builder.Default
    private List<DatasetRow> rows = new ArrayList<>();
}