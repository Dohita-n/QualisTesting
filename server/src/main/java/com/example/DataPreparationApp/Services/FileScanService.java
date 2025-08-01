package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileScanService {

    private final FileRepository fileRepository;

    @Async
    @Transactional
    public void scanFileAsync(File file) {
        try {
            log.info("Starting virus scan for file: {}", file.getOriginalName());
            
            // Simulate a virus scan (this would be replaced with actual ClamAV integration)
            boolean scanResult = performVirusScan(file.getStoredPath());
            
            // Update file status based on scan result
            updateFileStatus(file, scanResult);
            
            log.info("Virus scan completed for file: {}. Result: {}", 
                     file.getOriginalName(), file.getVirusScanStatus());
        } catch (Exception e) {
            log.error("Error during virus scan for file: {}", file.getOriginalName(), e);
            handleScanError(file);
        }
    }
    
    @Transactional(noRollbackFor = Exception.class)
    public void updateFileStatus(File file, boolean scanResult) {
        try {
            File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
            
            if (scanResult) {
                refreshedFile.setVirusScanStatus(File.VirusScanStatus.CLEAN);
            } else {
                refreshedFile.setVirusScanStatus(File.VirusScanStatus.INFECTED);
                refreshedFile.setStatus(File.FileStatus.FAILED);
            }
            
            fileRepository.save(refreshedFile);
        } catch (Exception ex) {
            log.error("Failed to update file status after virus scan: {}", ex.getMessage());
            // Try again with a simple update
            retryStatusUpdate(file, scanResult);
        }
    }
    
    private void retryStatusUpdate(File file, boolean scanResult) {
        try {
            File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
            refreshedFile.setVirusScanStatus(scanResult ? File.VirusScanStatus.CLEAN : File.VirusScanStatus.INFECTED);
            if (!scanResult) {
                refreshedFile.setStatus(File.FileStatus.FAILED);
            }
            fileRepository.save(refreshedFile);
        } catch (Exception e) {
            log.error("Failed to retry update file status: {}", e.getMessage());
        }
    }
    
    private void handleScanError(File file) {
        try {
            File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
            refreshedFile.setVirusScanStatus(File.VirusScanStatus.PENDING);
            refreshedFile.setStatus(File.FileStatus.FAILED);
            fileRepository.save(refreshedFile);
        } catch (Exception ex) {
            log.error("Failed to handle scan error: {}", ex.getMessage());
        }
    }
    
    private boolean performVirusScan(String filePath) throws IOException {
        // Simulated virus scan - doesn't make actual network connections
        Path path = Paths.get(filePath);
        log.info("Scanning file at path: {}", path.toAbsolutePath());
        
        // Simulate scan delay
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // For demo purposes, always return true (file is clean)
        return true;
    }
} 