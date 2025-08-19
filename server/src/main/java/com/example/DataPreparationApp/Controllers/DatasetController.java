package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Repository.DatasetColumnStatisticsRepository;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.example.DataPreparationApp.Services.AuthorizationService;
import com.example.DataPreparationApp.Services.ValidationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

//DatasetController its purpose relies on manages dataset operations after files are processed
//List dataset files, view their details across columns and rows

@RestController
@RequestMapping("/api/datasets")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DatasetController extends BaseController {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DatasetRowRepository datasetRowRepository;
    private final DatasetColumnStatisticsRepository datasetColumnStatisticsRepository;
    private final ValidationService validationService;
    private final JdbcTemplate jdbcTemplate;

    public DatasetController(
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            DatasetRowRepository datasetRowRepository,
            DatasetColumnStatisticsRepository datasetColumnStatisticsRepository,
            ValidationService validationService,
            JdbcTemplate jdbcTemplate,
            AuthorizationService authorizationService) {
        super(authorizationService);
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.datasetRowRepository = datasetRowRepository;
        this.datasetColumnStatisticsRepository = datasetColumnStatisticsRepository;
        this.validationService = validationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<List<Dataset>> getAllDatasets(@RequestParam UUID userId, @RequestParam(required = false) UUID requestedByUserId) throws Exception {
        List<Dataset> datasets = executeOperation(() -> {
            // If requestedByUserId is not provided, assume the requesting user is the same as userId
            UUID requestingUserId = (requestedByUserId != null) ? requestedByUserId : userId;

            if (userId.equals(requestingUserId) || authorizationService.hasViewAllDatasetsPermission(requestingUserId)) {
                // User is either viewing their own datasets or has permission to view all
                User user = new User();
                user.setId(userId);

                return datasetRepository.findByFile_User(user);
            } else {
                // User is trying to view someone else's datasets without permission
                throw new SecurityException("You do not have permission to view these datasets");
            }
        });

        return ResponseEntity.ok(datasets);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<Dataset> getDatasetById(@PathVariable UUID id, @RequestParam UUID userId) throws Exception {
        Dataset dataset = executeOperation(() -> {
            Dataset foundDataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));

            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, foundDataset);

            return foundDataset;
        });

        return ResponseEntity.ok(dataset);
    }

    @GetMapping("/{id}/columns")
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<List<DatasetColumn>> getDatasetColumns(@PathVariable UUID id, @RequestParam UUID userId) throws Exception {
        List<DatasetColumn> columns = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));

            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);

            return datasetColumnRepository.findByDatasetOrderByPosition(dataset);
        });

        return ResponseEntity.ok(columns);
    }

    @GetMapping("/{id}/rows")
    @PreAuthorize("hasAnyAuthority('VIEW_DATA', 'EDIT_DATA', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getDatasetRows(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) throws Exception { 
        Map<String, Object> result = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));

            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);

            Pageable pageable = PageRequest.of(page, size, Sort.by("rowNumber"));
            Page<DatasetRow> rows = datasetRowRepository.findByDataset(dataset, pageable);

            return Map.of(
                    "content", rows.getContent(),
                    "totalPages", rows.getTotalPages(),
                    "totalElements", rows.getTotalElements(),
                    "currentPage", rows.getNumber()
            );
        });

        return ResponseEntity.ok(result);
    }

    //Add a PUT Mapping to edit dataset name and description;

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
    public ResponseEntity<Dataset> updateDataset(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @RequestBody Map<String, String> updates) throws Exception {

        Dataset updatedDataset = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));

            // Check if the user has permission to edit this dataset
            checkDatasetAccess(userId, dataset);

            // Additional check for edit permission
            if (!authorizationService.hasEditDataPermission(userId) &&
                !authorizationService.isAdmin(userId)) {
                throw new SecurityException("You do not have permission to edit this dataset");
            }

            // Update dataset fields
            if (updates.containsKey("name")) {
                dataset.setName(updates.get("name"));
            }

            if (updates.containsKey("description")) {
                dataset.setDescription(updates.get("description"));
            }

            // Save the updated dataset
            return datasetRepository.save(dataset);
        });

        return ResponseEntity.ok(updatedDataset);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('DELETE_DATA', 'ADMIN')")
    public ResponseEntity<?> deleteDataset(@PathVariable UUID id, @RequestParam UUID userId) throws Exception {
        return executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));

            // Check if the user has permission to delete this dataset
            checkDatasetAccess(userId, dataset);

            // Additional check for deletion permission
            if (!authorizationService.hasEditDataPermission(userId) &&
                !authorizationService.isAdmin(userId) &&
                !dataset.getFile().getUser().getId().equals(userId)) {
                throw new SecurityException("You do not have permission to delete this dataset");
            }

            // Delete all related rows first to avoid constraint violations
            datasetRowRepository.deleteByDataset(dataset);

            // Delete all related columns (will cascade delete statistics)
            datasetColumnRepository.deleteByDataset(dataset);

            // Delete the dataset itself
            datasetRepository.delete(dataset);

            return ResponseEntity.ok(Map.of(
                "message", "Dataset deleted successfully",
                "id", id.toString()
            ));
        });
    }
    @PutMapping("/{datasetId}/rows")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
    @Transactional
    public ResponseEntity<String> updateDatasetRows(
            @PathVariable UUID datasetId,
            @RequestParam UUID userId,
            @RequestBody List<Map<String, Object>> updatedRows) {

        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

        checkDatasetAccess(userId, dataset);

        ObjectMapper mapper = new ObjectMapper();

        java.util.Set<String> touchedColumns = new java.util.HashSet<>();
        for (Map<String, Object> row : updatedRows) {
            UUID rowId = null;
            if (row.containsKey("id") && row.get("id") != null) {
                try {
                    rowId = UUID.fromString(row.get("id").toString());
                } catch (IllegalArgumentException e) {
                    // id invalide, ignore ou gérer selon besoin
                }
            }

            JsonNode jsonNode = mapper.convertValue(row, JsonNode.class);

            DatasetRow datasetRow;
            if (rowId != null && datasetRowRepository.existsById(rowId)) {
                datasetRow = datasetRowRepository.findById(rowId).get();
                datasetRow.setData(jsonNode);
            } else {
                datasetRow = DatasetRow.builder()
                        .dataset(dataset)
                        .data(jsonNode)
                        .rowNumber(row.containsKey("rowNumber") ? Integer.parseInt(row.get("rowNumber").toString()) : 0)
                        .build();
            }

            datasetRowRepository.save(datasetRow);

            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey();
                if (!"id".equalsIgnoreCase(key) && !"rowNumber".equalsIgnoreCase(key) && !"_modified".equalsIgnoreCase(key)) {
                    touchedColumns.add(key);
                }
            }
        }

        // Recompute validation stats for touched columns
        List<DatasetColumn> columns = datasetColumnRepository.findByDatasetOrderByPosition(dataset);
        for (DatasetColumn col : columns) {
            if (!touchedColumns.contains(col.getName())) continue;
            String pattern = datasetColumnStatisticsRepository.findByDatasetColumn(col)
                    .map(s -> s.getValidationPattern())
                    .orElse(null);
            if (pattern == null || pattern.isEmpty()) {
                pattern = defaultPattern(col);
            }
            try { validationService.validateColumn(col, pattern); } catch (Exception ignored) {}
        }

        return ResponseEntity.ok("Rows updated");
    }

    private String defaultPattern(DatasetColumn column) {
        if (column.getInferredDataType() == null) return "^.+$";
        switch (column.getInferredDataType()) {
            case INTEGER: return "^[-+]?\\d+$";
            case FLOAT:
            case DECIMAL: return "^[-+]?\\d*\\.?\\d+$";
            case DATE: return "^(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]\\d{4})$";
            case BOOLEAN: return "^(true|false|yes|f|t|no|0|1)$";
            case STRING:
            default: return "^.+$";
        }
    }

    // ---------------- Column management endpoints ----------------

    @PutMapping("/{id}/columns/{columnId}/rename")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
    @Transactional
    public ResponseEntity<DatasetColumn> renameColumn(
            @PathVariable UUID id,
            @PathVariable UUID columnId,
            @RequestParam UUID userId,
            @RequestBody Map<String, String> body) throws Exception {

        DatasetColumn updated = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));
            checkDatasetAccess(userId, dataset);

            DatasetColumn column = datasetColumnRepository.findById(columnId)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found with id: " + columnId));
            if (!column.getDataset().getId().equals(id)) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }

            String newName = body.get("name");
            if (newName == null || newName.trim().isEmpty()) {
                throw new IllegalArgumentException("New column name is required");
            }

            String oldName = column.getName();
            column.setName(newName.trim());
            DatasetColumn saved = datasetColumnRepository.save(column);

            // Rename key in JSONB data for all rows
            String sql = "UPDATE dataset_rows SET data = (data - ?) || jsonb_build_object(?, data->?) WHERE dataset_id = ?";
            jdbcTemplate.update(sql, oldName, newName, oldName, id);

            // Clear validation caches
            validationService.clearDatasetValidationCache(id);

            // Recalculate stats for this column using existing or default pattern
            String pattern = datasetColumnStatisticsRepository.findByDatasetColumn(saved)
                    .map(s -> s.getValidationPattern())
                    .orElse(null);
            if (pattern == null || pattern.isEmpty()) {
                pattern = defaultPattern(saved);
            }
            try { validationService.validateColumn(saved, pattern); } catch (Exception ignored) {}

            return saved;
        });

        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/columns")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
    @Transactional
    public ResponseEntity<DatasetColumn> createColumn(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @RequestBody Map<String, Object> body) throws Exception {

        DatasetColumn created = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));
            checkDatasetAccess(userId, dataset);

            String name = (String) body.get("name");
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Column name is required");
            }

            Integer position = body.get("position") != null ? Integer.valueOf(body.get("position").toString()) : null;
            String typeStr = body.get("type") != null ? body.get("type").toString() : "STRING";
            Integer precision = body.get("decimalPrecision") != null ? Integer.valueOf(body.get("decimalPrecision").toString()) : null;
            Integer scale = body.get("decimalScale") != null ? Integer.valueOf(body.get("decimalScale").toString()) : null;

            // Determine insert position
            List<DatasetColumn> columns = datasetColumnRepository.findByDatasetOrderByPosition(dataset);
            int insertPos = (position != null && position >= 0 && position <= columns.size()) ? position : columns.size();

            // Shift positions for columns at or after insert position
            for (DatasetColumn c : columns) {
                if (c.getPosition() >= insertPos) {
                    c.setPosition(c.getPosition() + 1);
                    datasetColumnRepository.save(c);
                }
            }

            // Create new column
            DatasetColumn newCol = DatasetColumn.builder()
                    .dataset(dataset)
                    .name(name.trim())
                    .position(insertPos)
                    .inferredDataType(parseDataType(typeStr))
                    .decimalPrecision(precision)
                    .decimalScale(scale)
                    .build();
            DatasetColumn saved = datasetColumnRepository.save(newCol);

            // Add empty values for this key to all rows
            String sql = "UPDATE dataset_rows SET data = data || jsonb_build_object(?, '') WHERE dataset_id = ?";
            jdbcTemplate.update(sql, name, id);

            // Clear validation cache
            validationService.clearDatasetValidationCache(id);

            // Compute initial stats (will be mostly emptyCount)
            String pattern = defaultPattern(saved);
            try { validationService.validateColumn(saved, pattern); } catch (Exception ignored) {}

            return saved;
        });

        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}/columns/{columnId}")
    @PreAuthorize("hasAnyAuthority('DELETE_DATA', 'ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteColumn(
            @PathVariable UUID id,
            @PathVariable UUID columnId,
            @RequestParam UUID userId) throws Exception {

        Map<String, Object> response = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));
            checkDatasetAccess(userId, dataset);

            DatasetColumn column = datasetColumnRepository.findById(columnId)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found with id: " + columnId));
            if (!column.getDataset().getId().equals(id)) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }

            String colName = column.getName();
            int removedPos = column.getPosition();

            // Remove statistics for this column
            datasetColumnStatisticsRepository.deleteAllByDatasetColumnId(columnId);

            // Delete column entity
            datasetColumnRepository.delete(column);

            // Shift positions down
            List<DatasetColumn> columns = datasetColumnRepository.findByDatasetOrderByPosition(dataset);
            for (DatasetColumn c : columns) {
                if (c.getPosition() > removedPos) {
                    c.setPosition(c.getPosition() - 1);
                    datasetColumnRepository.save(c);
                }
            }

            // Remove key from JSONB data
            String sql = "UPDATE dataset_rows SET data = (data - ?) WHERE dataset_id = ?";
            jdbcTemplate.update(sql, colName, id);

            // Clear validation cache
            validationService.clearDatasetValidationCache(id);

            return Map.of("deleted", true, "columnId", columnId.toString());
        });

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/columns/{columnId}/type")
    @PreAuthorize("hasAnyAuthority('EDIT_DATA', 'ADMIN')")
    @Transactional
    public ResponseEntity<DatasetColumn> updateColumnType(
            @PathVariable UUID id,
            @PathVariable UUID columnId,
            @RequestParam UUID userId,
            @RequestBody Map<String, Object> body) throws Exception {

        DatasetColumn updated = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));
            checkDatasetAccess(userId, dataset);

            DatasetColumn column = datasetColumnRepository.findById(columnId)
                    .orElseThrow(() -> new IllegalArgumentException("Column not found with id: " + columnId));
            if (!column.getDataset().getId().equals(id)) {
                throw new IllegalArgumentException("Column does not belong to the specified dataset");
            }

            String typeStr = body.get("type") != null ? body.get("type").toString() : null;
            if (typeStr == null) {
                throw new IllegalArgumentException("Type is required");
            }
            column.setInferredDataType(parseDataType(typeStr));
            if (column.getInferredDataType() == DatasetColumn.DataType.DECIMAL) {
                Integer precision = body.get("decimalPrecision") != null ? Integer.valueOf(body.get("decimalPrecision").toString()) : column.getDecimalPrecision();
                Integer scale = body.get("decimalScale") != null ? Integer.valueOf(body.get("decimalScale").toString()) : column.getDecimalScale();
                column.setDecimalPrecision(precision);
                column.setDecimalScale(scale);
            } else {
                column.setDecimalPrecision(null);
                column.setDecimalScale(null);
            }

            DatasetColumn saved = datasetColumnRepository.save(column);

            // Reset stats for this column to force recomputation with default pattern
            datasetColumnStatisticsRepository.deleteAllByDatasetColumnId(columnId);
            validationService.clearValidationCache(columnId);

            // Recalculate stats using default pattern for new type
            String pattern = defaultPattern(saved);
            try { validationService.validateColumn(saved, pattern); } catch (Exception ignored) {}

            return saved;
        });

        return ResponseEntity.ok(updated);
    }

    private DatasetColumn.DataType parseDataType(String typeStr) {
        try {
            return DatasetColumn.DataType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            return DatasetColumn.DataType.STRING;
        }
    }
}