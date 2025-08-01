package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.DTO.FileUploadResponse;
import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final FileRepository fileRepository;
    private final UserService userService;
    private final FileScanService fileScanService;
    private final FileValidationService fileValidationService;
    private final DataProcessingService dataProcessingService;
    
    @Value("${file.upload.max-size:734003200}") // Default 700MB in bytes
    private long maxFileSize;
    
    @Value("${file.upload.dir}")
    private String uploadDir;

    @Transactional
    public File uploadFile(MultipartFile multipartFile, String description) throws IOException {
        // Get current user (we'll need to implement security later)
        User currentUser = userService.getCurrentUser();
        
        // Validate file size
        if (multipartFile.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds the maximum allowed size of " + (maxFileSize / (1024 * 1024)) + " MB");
        }
        
        // Determine file type
        String originalFilename = multipartFile.getOriginalFilename();
        File.FileType fileType = determineFileType(originalFilename);
        
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Generate a unique filename
        String uniqueFilename = UUID.randomUUID() + "-" + originalFilename;
        Path filePath = uploadPath.resolve(uniqueFilename);
        
        // Save file to disk
        Files.copy(multipartFile.getInputStream(), filePath);
        
        // Create file entity
        File file = File.builder()
                .user(currentUser)
                .originalName(originalFilename)
                .storedPath(filePath.toString())
                .fileType(fileType)
                .size(multipartFile.getSize())
                .uploadDate(LocalDateTime.now())
                .status(File.FileStatus.UPLOADED)
                .virusScanStatus(File.VirusScanStatus.PENDING)
                .build();
        
        // Save file metadata to database
        File savedFile = fileRepository.save(file);
        
        // Trigger async virus scan and validation
        fileScanService.scanFileAsync(savedFile);
        fileValidationService.validateFileAsync(savedFile);
        
        return savedFile;
    }

    /**
     * Upload file and create dataset immediately
     */
    @Transactional
    public FileUploadResponse uploadFileAndCreateDataset(MultipartFile multipartFile, String description) throws IOException {
        // Upload the file first
        File uploadedFile = uploadFile(multipartFile, description);
        
        // Create dataset immediately
        Dataset dataset = null;
        try {
            dataset = dataProcessingService.createDatasetFromFile(uploadedFile);
            log.info("Dataset created successfully for file: {}", uploadedFile.getOriginalName());
        } catch (Exception e) {
            log.error("Error creating dataset for file: {}", uploadedFile.getOriginalName(), e);
            // Don't throw the exception, just log it and return the file info
            // The dataset can be created later via async processing
        }
        
        return FileUploadResponse.builder()
                .fileId(uploadedFile.getId())
                .datasetId(dataset != null ? dataset.getId() : null)
                .originalName(uploadedFile.getOriginalName())
                .fileType(uploadedFile.getFileType())
                .status(uploadedFile.getStatus())
                .message("File uploaded successfully" + (dataset != null ? " and dataset created" : ""))
                .build();
    }
    
    private File.FileType determineFileType(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }
        
        // Check if filename contains a dot for extension
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("File must have a valid extension");
        }
        
        String extension = filename.substring(lastDotIndex + 1).toLowerCase();
        
        return switch (extension) {
            case "csv" -> File.FileType.CSV;
            case "xlsx", "xls" -> File.FileType.XLSX;
            case "json" -> File.FileType.JSON;
            //case "sql" -> File.FileType.SQL;
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
    
    public File getFileById(UUID id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("File not found with id: " + id));
    }
    
    public List<File> getAllFiles() {
        // In a real application, you would filter by the current user
        return fileRepository.findAll();
    }
} 