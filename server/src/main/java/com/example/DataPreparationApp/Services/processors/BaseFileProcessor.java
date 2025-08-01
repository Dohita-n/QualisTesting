package com.example.DataPreparationApp.Services.processors;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Model.File;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Transactional
public abstract class BaseFileProcessor implements FileProcessor {
    protected final DatasetRepository datasetRepository;
    protected final DatasetColumnRepository datasetColumnRepository;
    protected final DatasetRowRepository datasetRowRepository;
    protected final ObjectMapper objectMapper;
    
    private static final int BATCH_SIZE = 1000;
    
    protected BaseFileProcessor(DatasetRepository datasetRepository,
                              DatasetColumnRepository datasetColumnRepository,
                              DatasetRowRepository datasetRowRepository,
                              ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.datasetRowRepository = datasetRowRepository;
        this.objectMapper = objectMapper;
    }
    
    protected Dataset createDataset(File file, String name) {
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setFile(file);
        dataset.setRowCount(0L);
        dataset.setColumnCount(0);
        return datasetRepository.save(dataset);
    }
    
    protected DatasetColumn createColumn(Dataset dataset, String name, int position) {
        DatasetColumn column = new DatasetColumn();
        column.setName(name);
        column.setDataset(dataset);
        column.setPosition(position);
        column.setInferredDataType(DatasetColumn.DataType.STRING);
        return column;
    }
    
    protected void saveRowsInBatches(List<DatasetRow> rows) {
        List<DatasetRow> batch = new ArrayList<>(BATCH_SIZE);
        
        for (DatasetRow row : rows) {
            batch.add(row);
            if (batch.size() >= BATCH_SIZE) {
                datasetRowRepository.saveAll(batch);
                batch.clear();
            }
        }
        
        if (!batch.isEmpty()) {
            datasetRowRepository.saveAll(batch);
        }
    }
    
    protected ObjectNode createRowData(List<DatasetColumn> columns, Map<String, String> values) {
        ObjectNode jsonData = objectMapper.createObjectNode();
        columns.forEach(column -> {
            String value = values.get(column.getName());
            if (value != null) {
                jsonData.put(column.getName(), value);
            } else {
                jsonData.putNull(column.getName());
            }
        });
        return jsonData;
    }
    
    protected void updateDatasetCounts(Dataset dataset, int columnCount, long rowCount) {
        dataset.setColumnCount(columnCount);
        dataset.setRowCount(rowCount);
        datasetRepository.save(dataset);
    }
    
    protected void updateDatasetColumnCount(Dataset dataset, int columnCount) {
        dataset.setColumnCount(columnCount);
        datasetRepository.save(dataset);
    }
    
    protected void updateDatasetRowCount(Dataset dataset, long rowCount) {
        dataset.setRowCount(rowCount);
        datasetRepository.save(dataset);
    }
}