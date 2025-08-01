import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { DataPreparationService } from './services/data-preparation.service';
import { Preparation } from './models/preparation.model';
import { Dataset } from '../../core/models/dataset.model';
import { DatasetService } from '../../core/services/dataset.service';
import { PreparationListComponent } from './components/preparation-list/preparation-list.component';

@Component({
  selector: 'app-data-preparation',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    PreparationListComponent
  ],
  templateUrl: './data-preparation.component.html',
  styleUrls: ['./data-preparation.component.css']
})
export class DataPreparationComponent implements OnInit {
  preparations: Preparation[] = [];
  isLoading = true;
  isAddModalOpen = false;
  preparationForm: FormGroup;
  datasets: Dataset[] = [];
  isSubmitting = false;
  errorMessage = '';

  constructor(
    private dataPreparationService: DataPreparationService,
    private datasetService: DatasetService,
    private fb: FormBuilder,
    private router: Router
  ) {
    this.preparationForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      description: [''],
      datasetId: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    this.loadPreparations();
    this.loadDatasets();
  }

  loadPreparations(): void {
    this.isLoading = true;
    console.log('Loading preparations...');
    this.dataPreparationService.getAllPreparations().subscribe({
      next: (data) => {
        // Ensure transformationSteps exists for each preparation
        this.preparations = data.map(prep => ({
          ...prep,
          transformationSteps: prep.transformationSteps || [],
          // If sourceDataset is missing but dataset exists, use dataset
          sourceDataset: prep.sourceDataset || (prep as any).dataset
        }));
        
        console.log('Processed preparations:', this.preparations);
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading preparations', err);
        this.isLoading = false;
      }
    });
  }

  loadDatasets(): void {
    console.log('Loading datasets...');
    this.datasetService.getAllDatasets().subscribe({
      next: (data) => {
        console.log('Datasets loaded:', data);
        this.datasets = data;
      },
      error: (err) => {
        console.error('Error loading datasets', err);
      }
    });
  }

  openAddModal(): void {
    this.isAddModalOpen = true;
    this.preparationForm.reset({
      name: '',
      description: '',
      datasetId: ''
    });
    this.errorMessage = '';
  }

  closeAddModal(): void {
    this.isAddModalOpen = false;
  }

  submitForm(): void {
    if (this.preparationForm.invalid) {
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';

    this.dataPreparationService.createPreparation(this.preparationForm.value).subscribe({
      next: (preparation) => {
        console.log('Preparation created:', preparation);
        this.isSubmitting = false;
        this.closeAddModal();
        this.router.navigate(['/data-preparation', preparation.id]);
      },
      error: (err) => {
        this.isSubmitting = false;
        this.errorMessage = err.error?.message || 'Failed to create preparation. Please try again.';
        console.error('Error creating preparation', err);
      }
    });
  }

  viewPreparation(preparation: Preparation | string): void {
    const preparationId = typeof preparation === 'string' ? preparation : preparation.id;
    this.router.navigate(['/data-preparation', preparationId]);
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'DRAFT':
        return 'status-draft';
      case 'PROCESSING':
        return 'status-processing';
      case 'EXECUTED':
        return 'status-complete';
      case 'ERROR':
        return 'status-error';
      default:
        return '';
    }
  }

  getFormattedDate(date: Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleDateString();
  }

  /**
   * Handle preparation deletion
   */
  handlePreparationDelete(preparationId: string): void {
    this.dataPreparationService.deletePreparation(preparationId).subscribe({
      next: () => {
        console.log('Preparation deleted successfully');
        this.loadPreparations(); // Reload the list after deletion
      },
      error: (err) => {
        console.error('Error deleting preparation', err);
        alert('Failed to delete preparation. Please try again.');
      }
    });
  }
}