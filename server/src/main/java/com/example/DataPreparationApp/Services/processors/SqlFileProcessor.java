/* package com.example.DataPreparationApp.Services.processors;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
//import com.example.DataPreparationApp.Utils.SqlParserUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


//This file is not used in the current version of the application
//It is kept for future improvements
@Component
public class SqlFileProcessor extends BaseFileProcessor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SqlFileProcessor.class);

    public SqlFileProcessor(DatasetRepository datasetRepository,
                          DatasetColumnRepository datasetColumnRepository,
                          DatasetRowRepository datasetRowRepository,
                          ObjectMapper objectMapper) {
        super(datasetRepository, datasetColumnRepository, datasetRowRepository, objectMapper);
    }

    @Override
    public Dataset processFile(File file) throws Exception {
        log.info("Processing SQL file: {}", file.getOriginalName());
        Dataset dataset = createDataset(file, file.getOriginalName().replace(".sql", ""));
        Path filePath = Paths.get(file.getStoredPath());
        String sqlContent = Files.readString(filePath);
        
        //List<String> columns = SqlParserUtil.extractTableColumns(sqlContent);
        
        List<DatasetColumn> datasetColumns = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            datasetColumns.add(createColumn(dataset, columns.get(i), i));
        }
        
        datasetColumnRepository.saveAll(datasetColumns);
        
        // Update the dataset with column count
        // For SQL files, we only extract the schema, not the data rows
        updateDatasetColumnCount(dataset, columns.size());
        
        return dataset;
    }
} */