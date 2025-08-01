package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.DTO.FileUploadResponse;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Services.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Slf4j
public class FileUploadController {

    private final FileUploadService fileUploadService; 

    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('UPLOAD_DATA', 'ADMIN')")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            log.info("Starting file upload: {} ({} bytes)", file.getOriginalFilename(), file.getSize());
            
            if (file.isEmpty()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "File is empty"));
            }
            
            FileUploadResponse response = fileUploadService.uploadFileAndCreateDataset(file, description);
            log.info("File upload completed successfully: {}", response.getOriginalName());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Validation error during file upload: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
                    
        } catch (Exception e) {
            log.error("Unexpected error during file upload: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred during file upload: " + e.getMessage()));
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<?> getFileDetails(@PathVariable UUID id) {
        try {
            File file = fileUploadService.getFileById(id);
            return ResponseEntity.ok(file);
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<?> getAllFiles() {
        return ResponseEntity.ok(fileUploadService.getAllFiles());
    }
} 