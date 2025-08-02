package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.Services.DataExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Slf4j
public class DataExportController {

    private final DataExportService dataExportService;

    /**
     * Export a dataset to CSV and return the file for download
     *
     * @param datasetId The ID of the dataset to export
     * @return A ResponseEntity with the CSV file as a downloadable resource
     */
    @GetMapping("/csv/{datasetId}")
    @PreAuthorize("hasAnyAuthority('EXPORT_DATA', 'ADMIN')")
    public ResponseEntity<Resource> exportDatasetToCsv(@PathVariable UUID datasetId) {
        try {
            // Generate the CSV file
            String filePath = dataExportService.exportDatasetToCsv(datasetId);
            
            // Create a Resource from the file path
            Path path = Paths.get("./uploads" + filePath);
            Resource resource = new UrlResource(path.toUri());
            
            // Extract the filename from the path
            String filename = path.getFileName().toString();
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
            } else {
                log.error("File not found or not readable: {}", path);
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            log.error("Error exporting dataset: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (MalformedURLException e) {
            log.error("Malformed URL for resource: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("IO error exporting dataset: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export a dataset to XLSX and return the file for download
     *
     * @param datasetId The ID of the dataset to export
     * @return A ResponseEntity with the XLSX file as a downloadable resource
     */
    @GetMapping("/xlsx/{datasetId}")
    @PreAuthorize("hasAnyAuthority('EXPORT_DATA', 'ADMIN')")
    public ResponseEntity<Resource> exportDatasetToXlsx(@PathVariable UUID datasetId) {
        try {
            // Generate the XLSX file
            String filePath = dataExportService.exportDatasetToXlsx(datasetId);
            
            // Create a Resource from the file path
            Path path = Paths.get("./uploads" + filePath);
            Resource resource = new UrlResource(path.toUri());
            
            // Extract the filename from the path
            String filename = path.getFileName().toString();
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
            } else {
                log.error("File not found or not readable: {}", path);
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            log.error("Error exporting dataset: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (MalformedURLException e) {
            log.error("Malformed URL for resource: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("IO error exporting dataset: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Export a dataset to XLS and return the file for download
     *
     * @param datasetId The ID of the dataset to export
     * @return A ResponseEntity with the XLS file as a downloadable resource
     */
    @GetMapping("/xls/{datasetId}")
    @PreAuthorize("hasAnyAuthority('EXPORT_DATA', 'ADMIN')")
    public ResponseEntity<Resource> exportDatasetToXls(@PathVariable UUID datasetId) {
        try {
            // Generate the XLS file
            String filePath = dataExportService.exportDatasetToXls(datasetId);
            
            // Create a Resource from the file path
            Path path = Paths.get("./uploads" + filePath);
            Resource resource = new UrlResource(path.toUri());
            
            // Extract the filename from the path
            String filename = path.getFileName().toString();
            
            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
            } else {
                log.error("File not found or not readable: {}", path);
                return ResponseEntity.notFound().build();
            }
        } catch (IllegalArgumentException e) {
            log.error("Error exporting dataset: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (MalformedURLException e) {
            log.error("Malformed URL for resource: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            log.error("IO error exporting dataset: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
} 