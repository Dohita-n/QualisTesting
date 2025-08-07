package com.example.DataPreparationApp.Services;

import com.example.DataPreparationApp.Model.DatasetColumn;
import com.example.DataPreparationApp.Repository.DatasetColumnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ColumnNameMigrationServiceTest {

    @Mock
    private DatasetColumnRepository datasetColumnRepository;

    @InjectMocks
    private ColumnNameMigrationService columnNameMigrationService;

    private DatasetColumn column1;
    private DatasetColumn column2;
    private DatasetColumn column3;

    @BeforeEach
    void setUp() {
        column1 = new DatasetColumn();
        column1.setId(java.util.UUID.randomUUID());
        column1.setName("Id");

        column2 = new DatasetColumn();
        column2.setId(java.util.UUID.randomUUID());
        column2.setName("  Name  ");

        column3 = new DatasetColumn();
        column3.setId(java.util.UUID.randomUUID());
        column3.setName("already_normalized");
    }

    @Test
    void testNeedsMigration_WithNonNormalizedColumns_ReturnsTrue() {
        // Given
        when(datasetColumnRepository.findAll()).thenReturn(Arrays.asList(column1, column2, column3));

        // When
        boolean needsMigration = columnNameMigrationService.needsMigration();

        // Then
        assertTrue(needsMigration);
        verify(datasetColumnRepository).findAll();
    }

    @Test
    void testNeedsMigration_WithAllNormalizedColumns_ReturnsFalse() {
        // Given
        column1.setName("id");
        column2.setName("name");
        when(datasetColumnRepository.findAll()).thenReturn(Arrays.asList(column1, column2, column3));

        // When
        boolean needsMigration = columnNameMigrationService.needsMigration();

        // Then
        assertFalse(needsMigration);
        verify(datasetColumnRepository).findAll();
    }

    @Test
    void testGetColumnsNeedingMigration_ReturnsCorrectList() {
        // Given
        when(datasetColumnRepository.findAll()).thenReturn(Arrays.asList(column1, column2, column3));

        // When
        List<String> columnsNeedingMigration = columnNameMigrationService.getColumnsNeedingMigration();

        // Then
        assertEquals(2, columnsNeedingMigration.size());
        assertTrue(columnsNeedingMigration.contains("'Id' -> 'id'"));
        assertTrue(columnsNeedingMigration.contains("'  Name  ' -> 'name'"));
        assertFalse(columnsNeedingMigration.contains("'already_normalized' -> 'already_normalized'"));
        verify(datasetColumnRepository).findAll();
    }

    @Test
    void testMigrateColumnNames_UpdatesColumnNames() {
        // Given
        when(datasetColumnRepository.findAll()).thenReturn(Arrays.asList(column1, column2, column3));
        when(datasetColumnRepository.save(any(DatasetColumn.class))).thenReturn(column1);

        // When
        columnNameMigrationService.migrateColumnNames();

        // Then
        verify(datasetColumnRepository).findAll();
        verify(datasetColumnRepository, times(2)).save(any(DatasetColumn.class));
        
        // Verify that column names were updated
        assertEquals("id", column1.getName());
        assertEquals("name", column2.getName());
        assertEquals("already_normalized", column3.getName());
    }

    @Test
    void testMigrateColumnNames_WithNullColumnName_HandlesGracefully() {
        // Given
        column1.setName(null);
        when(datasetColumnRepository.findAll()).thenReturn(Arrays.asList(column1));

        // When
        columnNameMigrationService.migrateColumnNames();

        // Then
        verify(datasetColumnRepository).findAll();
        verify(datasetColumnRepository).save(column1);
        assertEquals("", column1.getName());
    }

    @Test
    void testMigrateColumnNames_WithEmptyColumnName_HandlesGracefully() {
        // Given
        column1.setName("");
        when(datasetColumnRepository.findAll()).thenReturn(Arrays.asList(column1));

        // When
        columnNameMigrationService.migrateColumnNames();

        // Then
        verify(datasetColumnRepository).findAll();
        verify(datasetColumnRepository, never()).save(any(DatasetColumn.class));
        assertEquals("", column1.getName());
    }
} 