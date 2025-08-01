package com.example.DataPreparationApp.Repository;

import com.example.DataPreparationApp.Model.Dataset;
import com.example.DataPreparationApp.Model.DatasetRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface DatasetRowRepository extends JpaRepository<DatasetRow, UUID> {
    Page<DatasetRow> findByDataset(Dataset dataset, Pageable pageable);
    long countByDataset(Dataset dataset);
    
    @Modifying
    @Transactional
    void deleteByDataset(Dataset dataset);
    
    /**
     * Find all rows for a dataset ordered by row number
     */
    List<DatasetRow> findByDatasetIdOrderByRowNumber(UUID datasetId);
    
    /**
     * Find rows for a dataset ordered by row number with pagination
     */
    @Query("SELECT r FROM DatasetRow r WHERE r.dataset.id = :datasetId ORDER BY r.rowNumber")
    Page<DatasetRow> findByDatasetIdOrderByRowNumberPaged(
            @Param("datasetId") UUID datasetId, 
            Pageable pageable);
    
    /**
     * Find rows for a dataset with limit
     * Using Pageable instead of JPQL LIMIT 
     */
    default List<DatasetRow> findByDatasetIdOrderByRowNumberLimit(UUID datasetId, int limit) {
        return findByDatasetIdOrderByRowNumberPaged(datasetId, PageRequest.of(0, limit)).getContent();
    }
    
    /**
     * Find rows using native SQL query in case JPQL approaches fail
     * This is a fallback method to ensure we can always retrieve rows
     */
    @Query(value = "SELECT * FROM dataset_rows WHERE dataset_id = :datasetId ORDER BY row_number LIMIT :limit", 
           nativeQuery = true)
    List<DatasetRow> findByDatasetIdNative(@Param("datasetId") UUID datasetId, @Param("limit") int limit);
    
    /**
     * Count rows for a dataset
     */
    @Query("SELECT COUNT(dr) FROM DatasetRow dr WHERE dr.dataset.id = :datasetId")
    long countByDatasetId(@Param("datasetId") UUID datasetId);
    
    /**
     * Find rows in a dataset in batches, ordered by row number
     * 
     * @param datasetId The dataset ID
     * @param offset The offset to start from (0-based)
     * @param limit The maximum number of rows to retrieve
     * @return List of dataset rows for the specified batch
     */
    @Query(value = "SELECT * FROM dataset_rows WHERE dataset_id = :datasetId ORDER BY row_number OFFSET :offset LIMIT :limit", 
           nativeQuery = true)
    List<DatasetRow> findBatchByDatasetIdOrderByRowNumber(
            @Param("datasetId") UUID datasetId,
            @Param("offset") int offset,
            @Param("limit") int limit);
    
    /**
     * Alternative batch query using pagination
     */
    default List<DatasetRow> findBatchByDatasetIdWithPagination(UUID datasetId, int offset, int limit) {
        int pageSize = limit;
        int pageNumber = offset / pageSize;
        return findByDatasetIdOrderByRowNumberPaged(datasetId, PageRequest.of(pageNumber, pageSize)).getContent();
    }
    
    /**
     * Save multiple rows in a batch
     */
    @Override
    <S extends DatasetRow> List<S> saveAll(Iterable<S> entities);
} 