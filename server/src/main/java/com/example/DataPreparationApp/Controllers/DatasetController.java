package com.example.DataPreparationApp.Controllers;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Model.DatasetRow;
import com.example.DataPreparationApp.Model.User;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import com.example.DataPreparationApp.Repository.DatasetRepository;
import com.example.DataPreparationApp.Repository.DatasetRowRepository;
import com.example.DataPreparationApp.Services.AuthorizationService;
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

//DatasetController its purpose relies on manages dataset operations after files are processed
//List dataset files, view their details across columns and rows

@RestController
@RequestMapping("/api/datasets")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DatasetController extends BaseController {

    private final DatasetRepository datasetRepository;
    private final DatasetColumnRepository datasetColumnRepository;
    private final DatasetRowRepository datasetRowRepository;

    public DatasetController(
            DatasetRepository datasetRepository,
            DatasetColumnRepository datasetColumnRepository,
            DatasetRowRepository datasetRowRepository,
            AuthorizationService authorizationService) {
        super(authorizationService);
        this.datasetRepository = datasetRepository;
        this.datasetColumnRepository = datasetColumnRepository;
        this.datasetRowRepository = datasetRowRepository;
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
            @RequestParam(defaultValue = "200") int size) throws Exception {  // ⬅️ Ici : defaultValue="200"
        Map<String, Object> result = executeOperation(() -> {
            Dataset dataset = datasetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Dataset not found with id: " + id));

            // Check if the user has permission to view this dataset
            checkDatasetAccess(userId, dataset);

            Pageable pageable = PageRequest.of(page, size, Sort.by("rowNumber")); // ⬅️ Ajoute le tri si nécessaire
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
        }

        return ResponseEntity.ok("Rows updated");
    }
}