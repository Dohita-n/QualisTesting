package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FileRepository extends JpaRepository<File, UUID> {
    List<File> findByUser(User user);
    List<File> findByFileType(File.FileType fileType);
    List<File> findByStatus(File.FileStatus status);
} 