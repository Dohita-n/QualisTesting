import { Component, OnDestroy, OnInit, ViewEncapsulation, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FileUploadModule } from 'ng2-file-upload';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DataService } from '../../core/services/data.service';
import { UtilityService } from '../../core/services/utility.service';
import { DatasetManagementService } from '../../core/services/dataset-management.service';
import { NotificationService } from '../../core/services/notification.service';
import { CustomConfirmationService } from '../../core/services/confirmation.service';
import { DatasetCardComponent } from '../../shared/components/dataset-card/dataset-card.component';
import { LoadingSpinnerComponent } from '../../shared/components/loading-spinner/loading-spinner.component';
import { ErrorMessageComponent } from '../../shared/components/error-message/error-message.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { File as FileModel } from '../../core/models/file.model';
import { Dataset } from '../../core/models/dataset.model';
import { Subscription, timer, interval, of } from 'rxjs';
import { take, switchMap, catchError, timeout, retry, delay } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, 
    FileUploadModule, 
    FormsModule, 
    DatasetCardComponent,
    LoadingSpinnerComponent,
    ErrorMessageComponent,
    EmptyStateComponent,
    ToastModule,
    ConfirmDialogModule
  ],

  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
  encapsulation: ViewEncapsulation.Emulated
})
export class DashboardComponent implements OnInit, OnDestroy {
  
  loadingState = {
  filter: false,
  datasets: false
};
  isDragOver = false;
  selectedFile: File | null = null;
  uploadProgress = 0;
  dataPreview: any[] = [];
  dataPreviewHeaders: string[] = [];
  totalRows = 0;
  currentPage = 0;
  totalPages = 0;
  pageSize = 10000; // 10000 rows as a limit for the data preview
  datasetId: string | null = null;
  datasetColumns: any[] = []; // Array to store column metadata for the data type editor
  isUploading = false;
  errorMessage: string | null = null;
  showPreview = false;
  isLoading = false;
  useClientPagination = false;
  allData: any[] = [];
  processingStatus: 'WAITING' | 'PROCESSING' | 'COMPLETE' | 'ERROR' = 'WAITING';
  processingMessage = '';
  public retryCount = 0;
  public maxRetries = 5;
  private subscriptions: Subscription[] = [];
  private lastUploadedFileId: string | null = null;
  
  // Datasets section properties
  userDatasets: Dataset[] = [];
  loadingDatasets = false;
  datasetsError: string | null = null;

  // New property to track pending type changes count
  pendingTypeChanges: number = 0;
  searchQuery: string = '';
  activeFilter: string = 'dateNewestToOldest'; // Default
  isFilterLoading: boolean = false;

  filterOptions = [
    { value: 'rowsHighToLow', label: 'Highest to Lowest Rows' },
    { value: 'rowsLowToHigh', label: 'Lowest to Highest Rows' },
    { value: 'columnsHighToLow', label: 'Most to Fewest Columns' },
    { value: 'columnsLowToHigh', label: 'Fewest to Most Columns' },
    { value: 'dateNewestToOldest', label: 'Newest to Oldest' },
    { value: 'dateOldestToNewest', label: 'Oldest to Newest' },
    { value: 'nameAZ', label: 'Name (A-Z)' },
    { value: 'nameZA', label: 'Name (Z-A)' }
  ];

  constructor(
    private dataService: DataService,
    private router: Router,
    private utilityService: UtilityService,
    private datasetManagementService: DatasetManagementService,
    public authService: AuthService,
    private messageService: MessageService,
    private cdRef: ChangeDetectorRef,
    private notificationService: NotificationService,
    private confirmationService: CustomConfirmationService
  ) {}
  
  ngOnInit(): void {
    this.loadUserDatasets();
    
    // Subscribe to dataset changes to refresh the list if needed
    const changesSub = this.datasetManagementService.datasetsChanged$
      .subscribe(() => this.loadUserDatasets());
    this.subscriptions.push(changesSub);
  }

  ngOnDestroy(): void {
    // Clean up subscriptions
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }

  /**
   * Load all datasets belonging to the current user
   */
  loadUserDatasets(): void {
    this.loadingDatasets = true;
    this.datasetsError = null;
    
    const subscription = this.datasetManagementService.loadUserDatasets()
      .subscribe({
        next: (datasets) => {
          this.userDatasets = datasets;
          this.loadingDatasets = false;
          console.log('Datasets loaded successfully:', datasets);
        },
        error: (error) => {
          console.error('Error loading datasets:', error);
          this.datasetsError = 'Failed to load your datasets. Please try again later.';
          this.loadingDatasets = false;
        }
      });
      
    this.subscriptions.push(subscription);
  }

  /**
   * Format file size to human-readable format
   */
  formatFileSize(bytes: number): string {
    return this.utilityService.formatFileSize(bytes);
  }

  /**
   * Format date to a user-friendly string
   */
  formatDate(dateInput: string | Date): string {
    return this.utilityService.formatDate(dateInput);
  }

  /**
   * View dataset details - redirects to dataset detail page now
   */

  navigateToDatasets() {
    if (!this.isUploading) {
      this.router.navigate(['/datasets']);
    }
  }
  
  /**
   * Handle view dataset event
   */
  onViewDataset(dataset: Dataset): void {
    this.router.navigate(['/datasets', dataset.id]);
  }

  /**
   * Handle delete dataset event
   */
  async onDeleteDataset(data: {dataset: Dataset, event: Event}): Promise<void> {
    // Stop event propagation to prevent viewDataset from being called
    data.event.stopPropagation();
    
    // Afficher un toast d'avertissement
    this.notificationService.showWarning(
      `Êtes-vous sûr de vouloir supprimer le dataset "${data.dataset.name}" ? Cette action ne peut pas être annulée.`,
      'Confirmation de suppression',
      { sticky: true, life: 0 }
    );
    
    // Demander confirmation à l'utilisateur avec le service de confirmation
    const confirmed = await this.confirmationService.confirmDelete(data.dataset.name);
    if (confirmed) {
      this.deleteDataset(data.dataset.id);
    }
  }

  /**
   * Navigate to data profiler for statistics
   */
  onViewStatistics(dataset: Dataset): void {
    this.router.navigate(['/data-profiler', dataset.id]);
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    
    if (event.dataTransfer && event.dataTransfer.files.length > 0) {
      this.selectedFile = event.dataTransfer.files[0];
    }
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
    }
  }

  isValidFileType(file: File): boolean {
    const validExtensions = ['.csv', '.xlsx', '.xls'];
    const fileName = file.name.toLowerCase();
    return validExtensions.some(ext => fileName.endsWith(ext));
  }

  togglePaginationMode() {
    // No-op - pagination is removed
    console.log('Pagination is disabled - all data is loaded at once');
  }

   uploadFile() {
    if (!this.selectedFile) return;
    
    if (!this.isValidFileType(this.selectedFile)) {
      this.errorMessage = 'Invalid file type. Please upload a CSV or Excel file (.csv, .xlsx, .xls).';
      return;
    }

    console.log('Starting file upload for:', this.selectedFile.name);
    this.isUploading = true;
    this.errorMessage = null;
    this.uploadProgress = 0;
    this.retryCount = 0;
    this.processingStatus = 'WAITING';
    this.processingMessage = 'Preparing file upload...';
    
    // Reset data
    this.showPreview = false;
    this.allData = [];
    this.dataPreview = [];
    this.dataPreviewHeaders = [];

    // Use the smart upload method instead of regular upload
    const subscription = this.dataService.smartUploadFile(this.selectedFile).subscribe({
      next: (response: any) => {
        // Handle progress updates during chunked uploads
        if (response.isChunkUpload) {
          this.uploadProgress = response.uploadProgress;
          this.processingMessage = `Uploading file... ${this.uploadProgress}%`;
          return;
        }
        
        // Final response from the server
        console.log('Upload successful, response:', response);
        this.uploadProgress = 100;
        
        if (response && response.fileId) {
          console.log('File uploaded with ID:', response.fileId);
          this.lastUploadedFileId = response.fileId;
          
          if (response.datasetId) {
            console.log('Dataset created with ID:', response.datasetId);
            // Navigate directly to the dataset since it was created immediately
            this.navigateToDataset(response.datasetId);
          } else {
            // Fallback: if dataset creation failed, show error but file was uploaded
            this.isUploading = false;
            this.processingStatus = 'ERROR';
            this.processingMessage = 'File uploaded but dataset creation failed. Please check the datasets list.';
            this.loadUserDatasets(); // Refresh datasets list
          }
        } else {
          console.error('Missing file ID in response:', response);
          this.errorMessage = 'Error: The uploaded file did not return a valid ID.';
          this.isUploading = false;
          this.processingStatus = 'ERROR';
          this.processingMessage = 'Upload failed: missing file ID from server.';
        }
      },
      error: (error) => {
        console.error('Upload error:', error);
        this.isUploading = false;
        this.errorMessage = error.error?.message || error.message || 'Error uploading file. Please try again.';
        this.uploadProgress = 0;
        this.processingStatus = 'ERROR';
        this.processingMessage = `Upload failed: ${this.errorMessage}`;
      }
    });
    
    this.subscriptions.push(subscription);
  }

  cancelUpload() {
    this.selectedFile = null;
    this.uploadProgress = 0;
    this.errorMessage = null;
    this.processingStatus = 'WAITING';
    this.processingMessage = '';
    this.isUploading = false;
  }
  
  /**
   * Navigate to dataset using the datasetId from upload response
   */
  navigateToDataset(datasetId: string) {
    this.isLoading = false;
    this.isUploading = false;
    this.processingStatus = 'COMPLETE';
    this.processingMessage = 'Dataset created successfully. Redirecting...';
    
    // Navigate to the dataset page
    setTimeout(() => {
      this.router.navigate(['/datasets', datasetId]);
    }, 1000);
  }

  /**
   * Delete the dataset
   */
  deleteDataset(datasetId: string): void {
    this.isLoading = true;
    
    const subscription = this.datasetManagementService.deleteDataset(datasetId)
      .subscribe({
        next: () => {
          // Remove the deleted dataset from the list
          this.userDatasets = this.userDatasets.filter(d => d.id !== datasetId);
          this.isLoading = false;
          
          // Show success message
          this.notificationService.showSuccess('Dataset supprimé avec succès');
          this.errorMessage = null;
          
          // If dataset was being previewed, hide preview
          if (this.datasetId === datasetId) {
            this.showPreview = false;
          }
          
          // Notify other components about the change
          this.datasetManagementService.notifyDatasetsChanged();
        },
        error: (error) => {
          console.error('Error deleting dataset:', error);
          this.notificationService.showError('Échec de la suppression du dataset. Veuillez réessayer.');
          this.errorMessage = 'Failed to delete dataset. Please try again.';
          this.isLoading = false;
        }
      });
    
    this.subscriptions.push(subscription);
  }

  /**
   * Update the pending changes count based on iframe table component event
   */
  updatePendingChanges(count: number): void {
    this.pendingTypeChanges = count;
  }

  /**
   * Use ViewChild reference instead of manual registration
   * Remove the registerIframeTable method since we're using ViewChild now
   */
  applyTypeChanges(): void {
    // No-op - ViewChild reference is removed
    console.log('ViewChild reference is removed, no applyTypeChanges method');
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
 // 1. Déclarez une variable séparée pour le chargement


// 2. Modifiez votre getter pour éviter les effets secondaires
get filteredDatasets(): Dataset[] {
  if (!this.userDatasets || this.userDatasets.length === 0) return [];

  let filtered = this.userDatasets;

  if (this.searchQuery.trim()) {
    const query = this.searchQuery.toLowerCase().trim();
    filtered = filtered.filter(dataset =>
      dataset.name.toLowerCase().includes(query)
    );
  }

  if (this.activeFilter) {
    filtered = [...filtered].sort((a, b) => {
      switch (this.activeFilter) {
        case 'rowsHighToLow': return b.rowCount - a.rowCount;
        case 'rowsLowToHigh': return a.rowCount - b.rowCount;
        case 'columnsHighToLow': return b.columnCount - a.columnCount;
        case 'columnsLowToHigh': return a.columnCount - b.columnCount;
        case 'dateNewestToOldest': 
          return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
        case 'dateOldestToNewest':
          return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        case 'nameAZ': return a.name.localeCompare(b.name);
        case 'nameZA': return b.name.localeCompare(a.name);
        default: return 0;
      }
    });
  }

  return filtered;
}

// 3. Modifiez onFilterChange pour gérer le loading
onFilterChange(filterValue: string): void {
  this.loadingState.filter = true;
  this.activeFilter = filterValue;
  
  // Utilisez setTimeout pour laisser Angular terminer son cycle
  setTimeout(() => {
    this.loadingState.filter = false;
    this.cdRef.detectChanges(); // Si vous utilisez ChangeDetectorRef
  });
}

clearFilters(): void {
  this.searchQuery = '';
  this.activeFilter = '';
}

getFilterLabel(value: string): string {
  const filter = this.filterOptions.find(f => f.value === value);
  return filter ? filter.label : '';
}

onEditDataset(data: {dataset: Dataset, newName: string, event: Event}): void {
  const { dataset, newName, event } = data;
  event.stopPropagation();
  this.loadingDatasets = true;

  this.dataService.updateDataset(dataset.id, { name: newName })
    .subscribe({
      next: (updatedDataset) => {
        const index = this.userDatasets.findIndex(d => d.id === dataset.id);
        if (index !== -1) {
          this.userDatasets[index] = updatedDataset;
        }
        this.loadingDatasets = false;
        this.notificationService.showInfo(`Nom du dataset mis à jour : "${newName}"`);
      },
      error: (error) => {
        console.error('Error updating dataset:', error);
        this.loadingDatasets = false;
        this.notificationService.showError('Échec de la mise à jour du nom du dataset. Veuillez réessayer.');
      }
    });
}

}