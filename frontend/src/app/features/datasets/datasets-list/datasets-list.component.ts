import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DataService } from '../../../core/services/data.service';
import { UtilityService } from '../../../core/services/utility.service';
import { DatasetManagementService } from '../../../core/services/dataset-management.service';
import { NotificationService } from '../../../core/services/notification.service';
import { CustomConfirmationService } from '../../../core/services/confirmation.service';
import { Dataset } from '../../../core/models/dataset.model';
import { DatasetCardComponent } from '../../../shared/components/dataset-card/dataset-card.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { ErrorMessageComponent } from '../../../shared/components/error-message/error-message.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { Subscription } from 'rxjs';
import { MessageService, ConfirmationService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';

@Component({
  selector: 'app-datasets-list',
  standalone: true,
  imports: [
    CommonModule, 
    RouterModule, 
    FormsModule, 
    DatasetCardComponent,
    LoadingSpinnerComponent,
    ErrorMessageComponent,
    EmptyStateComponent,
    ToastModule,
    ConfirmDialogModule
  ],

  templateUrl: './datasets-list.component.html',
  styleUrls: ['./datasets-list.component.css']
})
export class DatasetsListComponent implements OnInit, OnDestroy {
  // Datasets section properties
  userDatasets: Dataset[] = [];
  loadingDatasets = false;
  datasetsError: string | null = null;
  private subscriptions: Subscription[] = [];

  // Search and filter properties
  searchQuery: string = '';
  activeFilter: string = 'dateNewestToOldest'; // Default sort: newest to oldest
  isFilterLoading: boolean = false;
  
  // Filter options
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
    private messageService: MessageService,
    private notificationService: NotificationService,
    private confirmationService: CustomConfirmationService
  ) {}
  
  ngOnInit(): void {
    this.loadUserDatasets();
    
    // Subscribe to dataset changes to refresh the list when needed
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
   * Format date to a user-friendly string
   */
  formatDate(dateInput: string | Date): string {
    return this.utilityService.formatDate(dateInput);
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
    const { dataset } = data;
    
    // Afficher un toast d'avertissement
    this.notificationService.showWarning(
      `Êtes-vous sûr de vouloir supprimer le dataset "${dataset.name}" ? Cette action ne peut pas être annulée.`,
      'Confirmation de suppression',
      { sticky: true, life: 0 }
    );
    
    const confirmed = await this.confirmationService.confirmDelete(dataset.name);
    if (confirmed) {
      this.deleteDataset(dataset.id);
    }
  }
  
  /**
   * Delete the dataset
   */
  deleteDataset(datasetId: string): void {
    this.loadingDatasets = true;
    
    const subscription = this.datasetManagementService.deleteDataset(datasetId)
      .subscribe({
        next: () => {
          // Remove the deleted dataset from the list
          this.userDatasets = this.userDatasets.filter(d => d.id !== datasetId);
          this.loadingDatasets = false;
          
          // Show success message
          this.notificationService.showSuccess('Dataset supprimé avec succès');
          this.datasetsError = null;
          
          // Notify other components about the change
          this.datasetManagementService.notifyDatasetsChanged();
        },
        error: (error) => {
          console.error('Error deleting dataset:', error);
          this.notificationService.showError('Échec de la suppression du dataset. Veuillez réessayer.');
          this.datasetsError = 'Failed to delete dataset. Please try again.';
          this.loadingDatasets = false;
        }
      });
    
    this.subscriptions.push(subscription);
  }

  /**
   * Handle statistics button click - Navigate to data profiler
   */
  onViewStatistics(dataset: Dataset): void {
    this.router.navigate(['/data-profiler', dataset.id]);
  }

  /**
   * Filter datasets based on search query
   */
  get filteredDatasets(): Dataset[] {
    if (!this.userDatasets || this.userDatasets.length === 0) {
      return [];
    }

    let filtered = this.userDatasets;

    // Apply search filter
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase().trim();
      filtered = filtered.filter(dataset => 
        dataset.name.toLowerCase().includes(query)
      );
    }

    // Apply sorting filters
    if (this.activeFilter) {
      this.isFilterLoading = true;
      
      switch (this.activeFilter) {
        case 'rowsHighToLow':
          filtered = [...filtered].sort((a, b) => b.rowCount - a.rowCount);
          break;
        case 'rowsLowToHigh':
          filtered = [...filtered].sort((a, b) => a.rowCount - b.rowCount);
          break;
        case 'columnsHighToLow':
          filtered = [...filtered].sort((a, b) => b.columnCount - a.columnCount);
          break;
        case 'columnsLowToHigh':
          filtered = [...filtered].sort((a, b) => a.columnCount - b.columnCount);
          break;
        case 'dateNewestToOldest':
          filtered = [...filtered].sort((a, b) => 
            new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
          );
          break;
        case 'dateOldestToNewest':
          filtered = [...filtered].sort((a, b) => 
            new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          );
          break;
        case 'nameAZ':
          filtered = [...filtered].sort((a, b) => 
            a.name.localeCompare(b.name)
          );
          break;
        case 'nameZA':
          filtered = [...filtered].sort((a, b) => 
            b.name.localeCompare(a.name)
          );
          break;
      }
      
      // Simulate a brief loading state for visual feedback
      setTimeout(() => {
        this.isFilterLoading = false;
      }, 300);
    }

    return filtered;
  }

  /**
   * Handle filter change
   */
  onFilterChange(filterValue: string): void {
    this.activeFilter = filterValue;
  }

  /**
   * Clear all filters
   */
  clearFilters(): void {
    this.searchQuery = '';
    this.activeFilter = '';
  }

  /**
   * Get filter label from value
   */
  getFilterLabel(value: string): string {
    const filter = this.filterOptions.find(f => f.value === value);
    return filter ? filter.label : '';
  }

  /**
   * Handle dataset name editing
   */
  onEditDataset(data: {dataset: Dataset, newName: string, event: Event}): void {
    const { dataset, newName, event } = data;
    event.stopPropagation();
    
    this.loadingDatasets = true;
    
    // Call the API to update the dataset name
    this.dataService.updateDataset(dataset.id, { name: newName })
      .subscribe({
        next: (updatedDataset) => {
          // Update the dataset in the local array
          const index = this.userDatasets.findIndex(d => d.id === dataset.id);
          if (index !== -1) {
            this.userDatasets[index] = updatedDataset;
          }
          
          this.loadingDatasets = false;
          
          // Show success message
          this.notificationService.showInfo(`Nom du dataset mis à jour : "${newName}"`);
        },
        error: (error) => {
          console.error('Error updating dataset:', error);
          this.loadingDatasets = false;
          
          // Show error message
          this.notificationService.showError('Échec de la mise à jour du nom du dataset. Veuillez réessayer.');
        }
      });
  }
} 