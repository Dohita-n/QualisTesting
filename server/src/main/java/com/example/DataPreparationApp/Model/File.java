package com.example.DataPreparationApp.Model;

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
@Table(name = "files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) //Prevents infinite recursion and circular references or unnecessary data being exposed.
public class File {

    @Id
    @GeneratedValue(generator= "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"files", "userProfiles"})
    private User user;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private FileType fileType;

    private Long size;

    @Column(name = "upload_date", nullable = false)
    private LocalDateTime uploadDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "virus_scan_status", nullable = false)
    private VirusScanStatus virusScanStatus;

    @Column(name = "schema_valid")
    private Boolean schemaValid;

    @Column(name = "job_id")
    private String jobId;


    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("file")
    private List<Dataset> datasets = new ArrayList<>();

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("file")
    private List<JobMetadata> jobMetadata = new ArrayList<>();
    
    public enum FileType {
        CSV, XLSX, XLS, JSON
    }

    public enum FileStatus {
        UPLOADED, PROCESSING, PROCESSED, FAILED
    }

    public enum VirusScanStatus {
        PENDING, CLEAN, INFECTED
    }
}