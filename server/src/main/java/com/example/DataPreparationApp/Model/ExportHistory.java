package com.example.DataPreparationApp.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "export_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportHistory {

    @Id
    @GeneratedValue(generator ="UUID")
    @GenericGenerator(name="UUID",strategy="org.hibernate.id.UUIDGenerator")
    @Column(name="id",updatable = false,nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "export_format", nullable = false)
    private String exportFormat;

    @Column(name = "export_path", nullable = false)
    private String exportPath;

    @Column(name = "exported_at", nullable = false)
    private LocalDateTime exportedAt;
}