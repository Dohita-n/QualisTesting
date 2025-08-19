import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { UtilityService } from '../../../core/services/utility.service';
import { Dataset } from '../../../core/models/dataset.model';
import { Subscription, switchMap, forkJoin, of, map, catchError, delay, switchMapTo, retry } from 'rxjs';
import { IframeTableComponent } from '../../../core/components/iframe-table/iframe-table.component';
import { FormsModule } from '@angular/forms';
//import { DataPreviewComponent } from '../../../features/dashboard/components/data-preview/data-preview.component';
import { DataService } from '@app/core/services/data.service';
import { SimpleNotificationService } from '../../../core/services/simple-notification.service';
import { ConfirmationService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
// Define interfaces for validation
export interface ValidationResult {
  validCount: number;
  invalidCount: number;
  emptyCount: number;
  pattern?: string;
}

export interface ColumnValidation {
  columnId: string;
  columnName: string;
  pattern: string;
  result: ValidationResult;
}

@Component({
  selector: 'app-dataset-view',
  standalone: true,
  imports: [CommonModule, RouterModule, IframeTableComponent, FormsModule, ToastModule, ConfirmDialogModule],

  templateUrl: './dataset-view.component.html',
  styleUrls: ['./dataset-view.component.css']
})
export class DatasetViewComponent implements OnInit, OnDestroy {
  @ViewChild(IframeTableComponent) iframeTableRef!: IframeTableComponent;
  modifiedRowsMap: Map<string, any> = new Map(); // clé = id, valeur = ligne modifiée
  datasetId: string | null = null;
  dataset: Dataset | null = null;
  currentPage: number = 0;
  totalPages: number = 0;
  pageSize: number = 200;
  datasetColumns: any[] = [];
  dataPreview: any[] = [];
  dataPreviewHeaders: string[] = [];
  isLoading: boolean = false;
  isLoadingValidations: boolean = false;
  errorMessage: string | null = null;
  processingStatus: 'WAITING' | 'PROCESSING' | 'COMPLETE' | 'ERROR' = 'WAITING';
  processingMessage: string = '';
  totalRows: number = 0;
  pendingTypeChanges: number = 0;
  hasUnsavedChanges = false;

  // Validation properties
  selectedColumnForValidation: string = '';
  selectedColumnName: string = '';
  validationPattern: string = '';
  validationResults: ValidationResult | null = null;
  columnValidationResults: { [columnId: string]: ColumnValidation } = {};
  
  private subscriptions: Subscription[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dataService: DataService,
    private utilityService: UtilityService,
    private notificationService: SimpleNotificationService
  ) {}

  ngOnInit(): void {
    // Get the dataset ID from the route
    const paramsSub = this.route.paramMap.pipe(
      switchMap(params => {
        this.datasetId = params.get('id');
        if (!this.datasetId) {
          this.navigateToDatasetsList('Dataset ID not found');
          return [];
        }

        this.isLoading = true;
        this.errorMessage = null;
        this.processingStatus = 'PROCESSING';
        this.processingMessage = 'Loading dataset...';

        // First get the dataset details
        return this.dataService.getDatasetById(this.datasetId);
      }),
      switchMap(dataset => {
        if (!dataset || !this.datasetId) {
          this.navigateToDatasetsList('Dataset not found');
          return [];
        }
        
        this.dataset = dataset;
        
        // Then get the columns to determine headers
        return this.dataService.getDatasetColumns(this.datasetId);
      }),
      switchMap(columns => {
        if (!this.datasetId) {
          return [];
        }
        
        this.datasetColumns = columns;
        this.dataPreviewHeaders = columns.map(col => col.name);
        
        // Then get the rows
        return this.dataService.getDatasetRows(this.datasetId, 0, this.pageSize);
      })
    ).subscribe({
      next: (response) => {
        this.dataPreview = response.content.map(row => {
          // Convert row data to object with column names as keys
          if (row.data) {
            return row.data;
          } else {
            // Fallback if data structure is different
            const rowData: any = {};
            this.dataPreviewHeaders.forEach(header => {
              rowData[header] = row[header] || null;
            });
            return rowData;
          }
        });
        
        this.totalRows = response.totalElements;
        this.currentPage = response.currentPage;
        this.totalPages = response.totalPages;
        this.isLoading = false;
        this.processingStatus = 'COMPLETE';
        this.processingMessage = 'Dataset loaded successfully.';
        this.loadDatasetPage(0);
        
        // Load validation information for each column
        if (this.datasetId) {
          this.loadColumnValidations();
        }
      },
      error: (error) => {
        console.error('Error loading dataset:', error);
        this.errorMessage = 'Failed to load dataset details.';
        this.isLoading = false;
        this.processingStatus = 'ERROR';
        this.processingMessage = 'Error loading dataset. Please try again.';
      }
    });
    
    this.subscriptions.push(paramsSub);
  }

  ngOnDestroy(): void {
     this.clearCache();
    // Clean up subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  private navigateToDatasetsList(errorMessage: string = ''): void {
    this.router.navigate(['/datasets']);
    if (errorMessage) {
      // You could add a service to handle notifications/alerts
      setTimeout(() => {
        alert(errorMessage);
      }, 100);
    }
  }

  /**
   * Format date to a user-friendly string
   */
  formatDate(dateInput: string | Date): string {
    return this.utilityService.formatDate(dateInput);
  }

  /**
   * Update the pending changes count based on iframe table component event
   */
  updatePendingChanges(count: number): void {
    this.pendingTypeChanges = count;
  }

  /**
   * Apply type changes from the iframe-table component
   */
  applyTypeChanges(): void {
    // Use the ViewChild reference to access the component directly
    if (this.iframeTableRef) {
      this.iframeTableRef.applyChanges();
    }
  }
  
  /**
   * Handler for when data types are saved in the iframe-table component
   */
  onTypeChangesSaved(): void {
    // Reset pending changes count
    this.pendingTypeChanges = 0;
    
    // Refresh column data after type changes are applied
    if (this.datasetId) {
      this.isLoading = true;
      this.dataService.getDatasetColumns(this.datasetId).subscribe({
        next: (columns) => {
          this.datasetColumns = columns; // Update columns with new types
          this.isLoading = false;
          
          // Show success message
          this.processingMessage = 'Data types updated successfully';
          setTimeout(() => {
            this.processingMessage = 'Dataset loaded successfully.';
          }, 3000);
        },
        error: (error) => {
          console.error('Error refreshing columns after type change:', error);
          this.isLoading = false;
        }
      });
    }
  }

  refreshColumnsAndValidations(): void {
    if (!this.datasetId) return;
    this.isLoading = true;
    this.dataService.getDatasetColumns(this.datasetId).subscribe({
      next: (columns) => {
        this.datasetColumns = columns;
        this.dataPreviewHeaders = columns.map(c => c.name);
        this.isLoading = false;
        // Reload validations for progress bars and cell indicators
        this.loadColumnValidations();
      },
      error: (e) => {
        console.error('Error refreshing columns', e);
        this.isLoading = false;
      }
    });
  }

  /**
   * Auto-generate validation pattern based on column data type
   */
  autoFetchPattern(): void {
    if (!this.selectedColumnForValidation) return;

    const selectedColumn = this.datasetColumns.find(col => col.id === this.selectedColumnForValidation);
    if (!selectedColumn) return;

    // Update selected column name for display
    this.selectedColumnName = selectedColumn.name;

    // Check if this column already has a validation pattern
    if (this.datasetId && this.columnValidationResults[selectedColumn.id]) {
      const existingPattern = this.columnValidationResults[selectedColumn.id].pattern;
      if (existingPattern) {
        this.validationPattern = existingPattern;
        this.validationResults = this.columnValidationResults[selectedColumn.id].result;
        return;
      }
    }

    // No existing pattern, generate one based on data type
    switch(selectedColumn.inferredDataType) {
      case 'INTEGER':
        this.validationPattern = '^[-+]?\\d+$';
        break;
      case 'DECIMAL':
      case 'FLOAT':
        this.validationPattern = '^[-+]?\\d*\\.?\\d+$';
        break;
      case 'DATE':
        this.validationPattern = '^(?:\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{4})$';
        break;
      case 'BOOLEAN':
        this.validationPattern = '^(true|false|yes|f|t|no|0|1)$';
        break;
      default:
        this.validationPattern = '.+';
    }
  }

saveDatasetRows(): void {
  console.log('saveDatasetRows called');
  
  if (!this.datasetId) {
    console.log('No dataset ID, showing error');
    this.notificationService.showError('Dataset ID manquant');
    return;
  }

  const modifiedRows = Array.from(this.modifiedRowsMap.values())
    .filter(row => row._modified); // Seulement les lignes réellement modifiées

  console.log('Modified rows count:', modifiedRows.length);

  if (modifiedRows.length === 0) {
    console.log('No modified rows, showing info');
    this.notificationService.showInfo('Aucune donnée modifiée à sauvegarder.');
    return;
  }

  console.log('Calling updateDatasetRows...');
  this.dataService.updateDatasetRows(this.datasetId, modifiedRows).subscribe({
    next: () => {
      console.log('Update successful, showing success message');
      this.notificationService.showSuccess('Changes saved successfully');

      this.hasUnsavedChanges = false;
      
      // Marque les lignes sauvegardées comme non modifiées mais conserve les valeurs
      modifiedRows.forEach(row => {
        if (row.id) {
          this.modifiedRowsMap.set(row.id, { ...row, _modified: false });
        }
      });
      
      // Pas besoin de recharger toute la page, juste mettre à jour le statut
      this.dataPreview = this.dataPreview.map(row => {
        if (modifiedRows.some(mr => mr.id === row.id)) {
          return { ...row, _modified: false };
        }
        return row;
      });
    },
    error: err => {
      console.log('Update failed, showing error:', err);
      this.notificationService.showError('Erreur lors de la sauvegarde : ' + err.message);
    }
  });
}


onDataChanged(updatedPageRows: any[]): void {
  updatedPageRows.forEach(row => {
    if (row._modified && row.id) {
      // Stocke seulement les champs modifiés pour minimiser la mémoire utilisée
      const existingChanges = this.modifiedRowsMap.get(row.id) || {};
      this.modifiedRowsMap.set(row.id, { 
        ...existingChanges,
        ...row,
        _modified: true 
      });
      this.hasUnsavedChanges = true;
    }
  });
}
clearCache(): void {
  this.modifiedRowsMap.clear();
  this.hasUnsavedChanges = false;
}


loadDatasetPage(page: number): void {
  if (!this.datasetId) {
    console.error('datasetId est null');
    return;
  }

  this.isLoading = true;
  this.dataService.getDatasetRows(this.datasetId, page, this.pageSize).subscribe({
    next: (response) => {
      // Transforme les données reçues
      let transformedData = response.content.map(row => ({
        id: row.id,
        rowNumber: row.rowNumber,
        ...row.data
      }));

      // Réapplique les modifications locales si elles existent
      transformedData = transformedData.map(row => {
        if (this.modifiedRowsMap.has(row.id)) {
          return { ...row, ...this.modifiedRowsMap.get(row.id), _modified: true };
        }
        return row;
      });

      this.dataPreview = transformedData;
      this.totalRows = response.totalElements;
      this.currentPage = response.currentPage;
      this.totalPages = response.totalPages;
      this.isLoading = false;
    },
    error: (error) => {
      console.error('Erreur lors du chargement des données :', error);
      this.errorMessage = 'Erreur lors du chargement des données.';
      this.isLoading = false;
    }
  });
}


  /**
   * Check validation results without persisting
   */
  checkValidation(): void {
    if (!this.datasetId || !this.selectedColumnForValidation || !this.validationPattern) return;

    const selectedColumn = this.datasetColumns.find(col => col.id === this.selectedColumnForValidation);
    if (!selectedColumn) return;

    // Update selected column name for display
    this.selectedColumnName = selectedColumn.name;

    try {
      // Create a RegExp object to validate the pattern - just to check syntax
      const regexPattern = new RegExp(this.validationPattern, 'i');
      
      // Show loading state
      this.isLoadingValidations = true;
      this.processingMessage = 'Checking validation...';
      
      // Perform client-side validation on the preview data
      let validCount = 0;
      let invalidCount = 0;
      let emptyCount = 0;
      
      // Only validate data available in the preview
      this.dataPreview.forEach(row => {
        const value = row[selectedColumn.name];
        
        if (value === null || value === undefined || value === '') {
          emptyCount++;
        } else {
          const stringValue = String(value);
          if (regexPattern.test(stringValue)) {
            validCount++;
          } else {
            invalidCount++;
          }
        }
      });
      
      // Update results with client-side validation
      this.validationResults = {
        validCount,
        invalidCount,
        emptyCount,
        pattern: this.validationPattern
      };
      
      this.isLoadingValidations = false;
      this.processingMessage = 'Validation check complete (client-side only)';
      
             // Show success toast message
       this.notificationService.showInfo('Client-side validation check completed. Apply to save this pattern.', 'Validation Check Complete');
    } catch (error) {
      console.error('Invalid regular expression pattern:', error);
      this.isLoadingValidations = false;
      
             this.notificationService.showError('Invalid regular expression pattern. Please check your syntax.', 'Invalid Pattern');
    }
  }

  /**
   * Apply validation pattern to selected column
   */
  applyValidation(): void {
    if (!this.datasetId || !this.selectedColumnForValidation || !this.validationPattern || !this.validationResults) {
      // If we don't have validation results yet, run the check first
      if (!this.validationResults) {
        this.checkValidation();
        return;
      }
      return;
    }

    const selectedColumn = this.datasetColumns.find(col => col.id === this.selectedColumnForValidation);
    if (!selectedColumn) return;

    // Update selected column name for display
    this.selectedColumnName = selectedColumn.name;

    try {
      // Create a RegExp object to validate the pattern - just to check syntax
      const regexPattern = new RegExp(this.validationPattern, 'i');
      
      // Show loading state
      this.isLoadingValidations = true;
      this.processingMessage = 'Applying validation...';
      
      // Call backend validation service
      this.dataService.validateColumn(
        this.datasetId, 
        this.selectedColumnForValidation, 
        this.validationPattern
      ).subscribe({
        next: (result) => {
          // Update results
          this.validationResults = {
            validCount: result.validCount,
            invalidCount: result.invalidCount,
            emptyCount: result.emptyCount,
            pattern: result.pattern
          };
          
          // Store the validation for the column
          this.columnValidationResults[selectedColumn.id] = {
            columnId: selectedColumn.id,
            columnName: selectedColumn.name,
            pattern: this.validationPattern,
            result: this.validationResults
          };
          
          this.isLoadingValidations = false;
          
                     // Show success toast message
           this.notificationService.showSuccess(`Validation pattern successfully applied to ${selectedColumn.name}`, 'Validation Applied');
        },
        error: (error) => {
          console.error('Error validating column:', error);
          this.isLoadingValidations = false;
          
                     // Check if this is a permission error (403 Forbidden)
           if (error.status === 403) {
             this.notificationService.showError('You need the EDIT_DATA role to apply validation patterns.', 'Permission Denied');
           } else {
             this.notificationService.showError('Error validating column. Please try again.', 'Validation Error');
           }
        }
      });
      
    } catch (error) {
      console.error('Invalid regular expression pattern:', error);
      this.errorMessage = 'Invalid regular expression pattern. Please check your syntax.';
      setTimeout(() => {
        this.errorMessage = null;
      }, 3000);
    }
  }

  /**
   * Get the percentage of valid values for the validation indicator
   */
  getValidPercentage(): number {
    if (!this.validationResults) return 0;
    
    const total = this.validationResults.validCount + 
                  this.validationResults.invalidCount + 
                  this.validationResults.emptyCount;
    
    return total > 0 ? (this.validationResults.validCount / total) * 100 : 0;
  }

  /**
   * Get the percentage of invalid values for the validation indicator
   */
  getInvalidPercentage(): number {
    if (!this.validationResults) return 0;
    
    const total = this.validationResults.validCount + 
                  this.validationResults.invalidCount + 
                  this.validationResults.emptyCount;
    
    return total > 0 ? (this.validationResults.invalidCount / total) * 100 : 0;
  }

  /**
   * Get the percentage of empty values for the validation indicator
   */
  getEmptyPercentage(): number {
    if (!this.validationResults) return 0;
    
    const total = this.validationResults.validCount + 
                  this.validationResults.invalidCount + 
                  this.validationResults.emptyCount;
    
    return total > 0 ? (this.validationResults.emptyCount / total) * 100 : 0;
  }

  /**
   * Load validation information for all columns
   */
  loadColumnValidations(): void {
    if (!this.datasetId || !this.datasetColumns.length) return;
    
    this.isLoadingValidations = true;
    this.errorMessage = null;
    this.columnValidationResults = {};
    
    // Create an array of validation requests for each column
    const validationObservables = this.datasetColumns.map(column => {
      return this.dataService.getColumnValidation(this.datasetId!, column.id).pipe(
        map(result => ({ [column.id]: { result, pattern: result.pattern } })),
        catchError(error => {
          console.error(`Error loading validation for column ${column.name}:`, error);
          
          // Check if the error is about duplicate statistics
          if (error.message && error.message.includes('More than one row with the given identifier was found')) {
            console.log('Attempting to fix duplicate statistics...');
            
            // Attempt to fix the duplicate statistics
            return this.dataService.fixDatasetDuplicateStatistics(this.datasetId!)
              .pipe(
                // Wait 500ms after fixing duplicates
                delay(500),
                // Then retry the original validation request
                switchMap(() => this.dataService.getColumnValidation(this.datasetId!, column.id)),
                map(result => ({ [column.id]: { result, pattern: result.pattern } })),
                catchError(retryError => {
                  console.error('Error after fixing duplicates:', retryError);
                  return of({ [column.id]: { 
                    result: { 
                      columnId: column.id,
                      columnName: column.name,
                      validCount: 0,
                      invalidCount: 0,
                      emptyCount: 0,
                      pattern: '' 
                    },
                    pattern: ''
                  }});
                })
              );
          }
          
          // Return empty results for this column
          return of({ [column.id]: { 
            result: { 
              columnId: column.id,
              columnName: column.name,
              validCount: 0,
              invalidCount: 0,
              emptyCount: 0,
              pattern: '' 
            },
            pattern: ''
          }});
        })
      );
    });
    
    // Execute all requests in parallel
    if (validationObservables.length > 0) {
      forkJoin(validationObservables).subscribe({
        next: (results) => {
          // Store all validation results
          let combinedResults = {};
          results.forEach(result => {
            combinedResults = { ...combinedResults, ...result };
          });
          this.columnValidationResults = combinedResults;
          this.isLoadingValidations = false;
          
          console.log('Loaded validation information for all columns', this.columnValidationResults);
        },
        error: (error) => {
          console.error('Error loading column validations:', error);
          this.errorMessage = 'Failed to load validation information.';
          this.isLoadingValidations = false;
        }
      });
    }
  }

    
  /**
   * Export the current dataset to CSV and trigger download
   */
  exportToCsv(): void {
    console.log('exportToCsv called');
    if (!this.datasetId) {
      console.error('No dataset ID provided for export');
      alert('Cannot export: Dataset ID is missing');
      return;
    }

    console.log('Exporting dataset to CSV:', this.datasetId);
    this.dataService.exportDatasetToCsv(this.datasetId).subscribe({
      next: (blob: Blob) => {
        console.log('CSV export successful, blob size:', blob.size);
        // Create a download link and trigger the download
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        document.body.appendChild(a);
        a.style.display = 'none';
        a.href = url;
        
        // Use dataset name if available, otherwise use timestamp
        const datasetName = this.dataset?.name || 'dataset';
        const sanitizedName = datasetName.replace(/[^a-zA-Z0-9]/g, '_');
        a.download = `${sanitizedName}.csv`;
        
        console.log('Downloading file as:', a.download);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
      },
      error: (error) => {
        console.error('Error exporting dataset to CSV:', error);
        alert('Failed to export dataset. Please try again.');
      }
    });
  }

  /**
   * Export the current dataset to XLSX and trigger download
   */
  exportToXlsx(): void {
    console.log('exportToXlsx called');
    if (!this.datasetId) {
      console.error('No dataset ID provided for export');
      alert('Cannot export: Dataset ID is missing');
      return;
    }

    console.log('Exporting dataset to XLSX:', this.datasetId);
    this.dataService.exportDatasetToXlsx(this.datasetId).subscribe({
      next: (blob: Blob) => {
        console.log('XLSX export successful, blob size:', blob.size);
        // Create a download link and trigger the download
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        document.body.appendChild(a);
        a.style.display = 'none';
        a.href = url;
        
        // Use dataset name if available, otherwise use timestamp
        const datasetName = this.dataset?.name || 'dataset';
        const sanitizedName = datasetName.replace(/[^a-zA-Z0-9]/g, '_');
        a.download = `${sanitizedName}.xlsx`;
        
        console.log('Downloading file as:', a.download);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
      },
      error: (error) => {
        console.error('Error exporting dataset to XLSX:', error);
        alert('Failed to export dataset. Please try again.');
      }
    });
  }

  /**
   * Export the current dataset to XLS and trigger download
   */
  exportToXls(): void {
    console.log('exportToXls called');
    if (!this.datasetId) {
      console.error('No dataset ID provided for export');
      alert('Cannot export: Dataset ID is missing');
      return;
    }

    console.log('Exporting dataset to XLS:', this.datasetId);
    this.dataService.exportDatasetToXls(this.datasetId).subscribe({
      next: (blob: Blob) => {
        console.log('XLS export successful, blob size:', blob.size);
        // Create a download link and trigger the download
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        document.body.appendChild(a);
        a.style.display = 'none';
        a.href = url;
        
        // Use dataset name if available, otherwise use timestamp
        const datasetName = this.dataset?.name || 'dataset';
        const sanitizedName = datasetName.replace(/[^a-zA-Z0-9]/g, '_');
        a.download = `${sanitizedName}.xls`;
        
        console.log('Downloading file as:', a.download);
        a.click();
        window.URL.revokeObjectURL(url);
        a.remove();
      },
      error: (error) => {
        console.error('Error exporting dataset to XLS:', error);
        alert('Failed to export dataset. Please try again.');
      }
    });
  }


} 