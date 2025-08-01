package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.FileRepository;
import com.example.DataPreparationApp.Services.processors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataProcessingService {

    private final FileRepository fileRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DataProfilingService dataProfilingService;
    
    // File processors
    private final CsvFileProcessor csvFileProcessor;
    //private final JsonFileProcessor jsonFileProcessor;
    private final ExcelFileProcessor excelFileProcessor;
    //private final SqlFileProcessor sqlFileProcessor;

    /**
     * Create dataset immediately from uploaded file
     */
    @Transactional
    public Dataset createDatasetFromFile(File file) throws Exception {
        log.info("Creating dataset immediately for file: {}", file.getOriginalName());
        
        // Update file status to processing
        updateFileStatusToProcessing(file);
        
        try {
            // Get the appropriate processor for the file type
            FileProcessor processor = getProcessorForFileType(file.getFileType());
            
            // Process the file and create dataset
            Dataset dataset = processFileWithTransaction(file, processor);
            
            if (dataset != null) {
                // Analyze the dataset for profiling
                List<DatasetColumn> columns = datasetColumnRepository.findByDataset(dataset);
                dataProfilingService.analyzeDataset(dataset, columns);
            }
            
            // Update file status to processed
            updateFileStatusToProcessed(file);
            
            log.info("Dataset created successfully for file: {}", file.getOriginalName());
            return dataset;
            
        } catch (Exception e) {
            log.error("Error creating dataset for file: {}", file.getOriginalName(), e);
            updateFileStatusToFailed(file, e);
            throw e;
        }
    }

    @Async
    public void processFileAsync(File file) {
        try {
            updateFileStatusToProcessing(file);
            
            FileProcessor processor = getProcessorForFileType(file.getFileType());
            Dataset dataset = processFileWithTransaction(file, processor);
            
            if (dataset != null) {
                List<DatasetColumn> columns = datasetColumnRepository.findByDataset(dataset);
                dataProfilingService.analyzeDataset(dataset, columns);
            }
            
            updateFileStatusToProcessed(file);
            
        } catch (Exception e) {
            updateFileStatusToFailed(file, e);
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFileStatusToProcessing(File file) {
        log.info("Starting data processing for file: {}", file.getOriginalName());
        File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
        refreshedFile.setStatus(File.FileStatus.PROCESSING);
        fileRepository.save(refreshedFile);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Dataset processFileWithTransaction(File file, FileProcessor processor) throws Exception {
        try {
            return processor.processFile(file);
        } catch (Exception e) {
            log.error("Error processing file {}: {}", file.getOriginalName(), e.getMessage());
            throw e; // Propagate to rollback this transaction only
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFileStatusToProcessed(File file) {
        File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
        refreshedFile.setStatus(File.FileStatus.PROCESSED);
        fileRepository.save(refreshedFile);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFileStatusToFailed(File file, Exception e) {
        File refreshedFile = fileRepository.findById(file.getId()).orElse(file);
        refreshedFile.setStatus(File.FileStatus.FAILED);
        fileRepository.save(refreshedFile);
    }

    private FileProcessor getProcessorForFileType(File.FileType fileType) {
        return switch (fileType) {
            case CSV -> csvFileProcessor;
            case XLSX -> excelFileProcessor;
            //case JSON -> jsonFileProcessor;
            //case SQL -> sqlFileProcessor;
            default -> throw new IllegalArgumentException("No processor available for: " + fileType);
        };
    }
}