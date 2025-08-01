package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Events.FileUploadCompletedEvent;
import com.example.DataPreparationApp.Events.FileScanCompletedEvent;
import com.example.DataPreparationApp.Events.FileValidatedEvent;
import com.example.DataPreparationApp.Model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service to listen to application events and trigger appropriate actions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventListenerService {

    private final FileScanService fileScanService;
    private final FileValidationService fileValidationService;
    private final DataProcessingService dataProcessingService;

    /**
     * When file upload is completed, trigger virus scan
     */
    @EventListener
    @Async
    public void handleFileUploadCompletedEvent(FileUploadCompletedEvent event) {
        File file = event.getFile();
        log.info("File upload completed, triggering virus scan for file: {}", file.getOriginalName());
        fileScanService.scanFileAsync(file);
    }
    
    /**
     * When file virus scan is completed successfully, trigger file validation
     */
    @EventListener
    @Async
    public void handleFileScanCompletedEvent(FileScanCompletedEvent event) {
        File file = event.getFile();
        log.info("File virus scan completed, triggering validation for file: {}", file.getOriginalName());
        fileValidationService.validateFileAsync(file);
    }
    
    /**
     * When file is validated successfully, trigger data processing
     */
    @EventListener
    @Async
    public void handleFileValidatedEvent(FileValidatedEvent event) {
        File file = event.getFile();
        log.info("File validation completed, triggering data processing for file: {}", file.getOriginalName());
        dataProcessingService.processFileAsync(file);
    }
} 