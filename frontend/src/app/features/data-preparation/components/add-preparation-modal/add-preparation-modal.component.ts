import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dataset } from '../../../../core/models/dataset.model';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { ErrorMessageComponent } from '../../../../shared/components/error-message/error-message.component';
import { UtilityService } from '../../../../core/services/utility.service';

@Component({
  selector: 'app-add-preparation-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LoadingSpinnerComponent,
    ErrorMessageComponent
  ],
  templateUrl: './add-preparation-modal.component.html',
  styleUrls: ['./add-preparation-modal.component.css']
})
export class AddPreparationModalComponent {
  @Input() datasets: Dataset[] = [];
  @Input() loading = false;
  @Input() error: string | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() create = new EventEmitter<{ name: string, datasetId: string }>();

  // Form data
  preparationName = '';
  selectedDatasetId = '';
  
  // Search and filter
  searchQuery = '';

  constructor(private utilityService: UtilityService) {}

  /**
   * Format date to a user-friendly string
   */
  formatDate(dateInput: string | Date): string {
    return this.utilityService.formatDate(dateInput);
  }

  /**
   * Handle modal close
   */
  onClose(): void {
    this.close.emit();
  }

  /**
   * Handle dataset selection
   */
  selectDataset(dataset: Dataset): void {
    this.selectedDatasetId = dataset.id;
  }

  /**
   * Create a new preparation
   */
  createPreparation(): void {
    if (!this.preparationName.trim()) {
      alert('Please enter a preparation name.');
      return;
    }

    if (!this.selectedDatasetId) {
      alert('Please select a dataset.');
      return;
    }

    this.create.emit({
      name: this.preparationName.trim(),
      datasetId: this.selectedDatasetId
    });
  }

  /**
   * Filter datasets based on search query
   */
  get filteredDatasets(): Dataset[] {
    if (!this.searchQuery.trim()) {
      return this.datasets;
    }

    const query = this.searchQuery.toLowerCase().trim();
    return this.datasets.filter(dataset => 
      dataset.name.toLowerCase().includes(query)
    );
  }
} 