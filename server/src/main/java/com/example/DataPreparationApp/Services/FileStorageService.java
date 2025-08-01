package com.example.DataPreparationApp.Services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    @Value("${app.file-storage.root-dir:./uploads}")
    private String rootDirectory;
    
    @Value("${app.base-url:https://localhost:8443}")
    private String baseUrl;

    /**
     * Store a file in the specified directory
     *
     * @param file The file to store
     * @param subDirectory The subdirectory within the root directory
     * @param fileName The desired filename
     * @return The URL path to access the file
     * @throws IOException If the file cannot be stored
     */
    public String storeFile(MultipartFile file, String subDirectory, String fileName) throws IOException {
        // Create the target directory if it doesn't exist
        Path directoryPath = Paths.get(rootDirectory, subDirectory);
        Files.createDirectories(directoryPath);

        // Sanitize the filename
        String sanitizedFileName = StringUtils.cleanPath(fileName);

        // Copy the file to the target location
        Path targetPath = directoryPath.resolve(sanitizedFileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // Return the full URL to the file using the server's base URL
        return baseUrl + "/files/" + subDirectory + "/" + sanitizedFileName;
    }

    /**
     * Delete a file at the specified path
     *
     * @param filePath The path of the file to delete
     * @return true if the file was deleted, false otherwise
     */
    public boolean deleteFile(String filePath) {
        try {
            // Extract the relative path from the URL
            String relativePath = filePath.replace(baseUrl, "").replaceFirst("/files/", "");
            Path targetPath = Paths.get(rootDirectory, relativePath);
            return Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            return false;
        }
    }
} 