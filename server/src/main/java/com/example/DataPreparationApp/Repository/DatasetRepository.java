package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
    List<Dataset> findByFile(File file);
    List<Dataset> findByFile_User(User user);
}