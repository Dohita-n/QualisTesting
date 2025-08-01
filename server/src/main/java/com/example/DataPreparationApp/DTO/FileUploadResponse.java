package com.example.DataPreparationApp.DTO;

import com.example.DataPreparationApp.Model.File;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private UUID fileId;
    private UUID datasetId;
    private String originalName;
    private File.FileType fileType;
    private File.FileStatus status;
    private String message;
} 