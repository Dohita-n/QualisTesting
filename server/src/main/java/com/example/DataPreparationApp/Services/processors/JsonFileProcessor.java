package com.example.DataPreparationApp.Services.processors;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;



//This file is not used in the current version of the application
//It is kept for future improvements

@Component
public class JsonFileProcessor extends BaseFileProcessor {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JsonFileProcessor.class);

    public JsonFileProcessor(DatasetRepository datasetRepository,
                           DatasetColumnRepository datasetColumnRepository,
                           DatasetRowRepository datasetRowRepository,
                           ObjectMapper objectMapper) {
        super(datasetRepository, datasetColumnRepository, datasetRowRepository, objectMapper);
    }

    @Override
    public Dataset processFile(File file) throws Exception {
        log.info("Processing JSON file: {}", file.getOriginalName());
        Dataset dataset = createDataset(file, file.getOriginalName().replace(".json", ""));
        Path filePath = Paths.get(file.getStoredPath());
        JsonNode rootNode = objectMapper.readTree(filePath.toFile());
        
        Map<String, Integer> columnPositions = new HashMap<>();
        List<DatasetColumn> columns = new ArrayList<>();
        int rowCount = 0;
        
        if (rootNode.isArray() && rootNode.size() > 0) {
            rowCount = processJsonArray(dataset, rootNode, columns, columnPositions);
        } else if (rootNode.isObject()) {
            rowCount = processJsonObject(dataset, rootNode, columns, columnPositions);
        }

        // Update the dataset with row and column counts
        updateDatasetCounts(dataset, columns.size(), rowCount);
        
        return dataset;
    }
    
    private int processJsonArray(Dataset dataset, JsonNode arrayNode, 
                                 List<DatasetColumn> columns, Map<String, Integer> positions) {
        // Implementation similar to original JSON processing
        // Return row count at the end of the method
        return arrayNode.size();
    }
    
    private int processJsonObject(Dataset dataset, JsonNode objectNode,
                                  List<DatasetColumn> columns, Map<String, Integer> positions) {
        // Implementation for single JSON object
        // Return 1 since it's a single object
        return 1;
    }
}