import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { DataPreparationService } from '../services/data-preparation.service';
import { Preparation } from '../models/preparation.model';
import { TransformationStep } from '../models/transformation-step.model';
import { PreparationPreviewDTO } from '../models/preparation-preview.model';
import { TransformationFormComponent } from '../components/transformation-form/transformation-form.component';
import { TransformationsListComponent } from '../components/transformations-list/transformations-list.component';
import { DatasetService } from '../../../core/services/dataset.service';

@Component({
  selector: 'app-preparation-detail',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    TransformationFormComponent,
    TransformationsListComponent
  ],
  templateUrl: './preparation-detail.component.html',
  styleUrls: ['./preparation-detail.component.css']
})
export class PreparationDetailComponent implements OnInit {
  preparationId: string = '';
  preparation: Preparation | null = null;
  previewData: PreparationPreviewDTO | null = null;
  
  isLoading = true;
  isPreviewLoading = false;
  isExecuting = false;
  errorMessage = '';
  
  selectedTransformationType: string = '';
  showTransformationForm = false;
  
  // Transformation types
  transformationTypes = [
    { value: 'FILTER_ROWS', label: 'Filter Rows' },
    { value: 'FILL_NULL', label: 'Fill Null Values' },
    { value: 'COLUMN_RENAME', label: 'Rename Column' },
    { value: 'COLUMN_DROP', label: 'Drop Column' },
    { value: 'COLUMN_FORMULA', label: 'Formula Calculation' }
  ];


  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dataPreparationService: DataPreparationService,
    private datasetService : DatasetService
  ) { }

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.preparationId = id;
        this.loadPreparation();
      } else {
        this.router.navigate(['/data-preparation']);
      }
    });
  }

  loadPreparation(): void {
    this.isLoading = true;
    this.errorMessage = '';
    
    this.dataPreparationService.getPreparationById(this.preparationId).subscribe({
      next: (preparation) => {
        this.preparation = preparation;
        // Add this code to ensure columns are loaded
        if (preparation.sourceDataset && preparation.sourceDataset.id) {
          this.datasetService.getDatasetColumns(preparation.sourceDataset.id)
            .subscribe(columns => {
              if (!preparation.sourceDataset.columns || preparation.sourceDataset.columns.length === 0) {
                preparation.sourceDataset.columns = columns;
              }
              this.isLoading = false;
              this.loadPreview();
            });
        } else {
          this.isLoading = false;
          this.loadPreview();
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err.error?.message || 'Failed to load preparation. Please try again.';
        console.error('Error loading preparation', err);
      }
    });
  }

  loadPreview(): void {
    this.isPreviewLoading = true;
    
    this.dataPreparationService.getPreview(this.preparationId).subscribe({
      next: (preview) => {
        this.previewData = preview;
        this.isPreviewLoading = false;
      },
      error: (err) => {
        this.isPreviewLoading = false;
        console.error('Error loading preview', err);
      }
    });
  }

  selectTransformationType(type: string): void {
    this.selectedTransformationType = type;
    this.showTransformationForm = true;
  }

  cancelTransformation(): void {
    this.showTransformationForm = false;
    this.selectedTransformationType = '';
  }

  applyTransformation(event: any): void {
    const transformationType = event.type;
    const parameters = event.parameters;
    
    console.log(`Preparing to apply transformation: ${transformationType}`, parameters);
    
    // Ensure we have required parameters based on transformation type
    let isValid = true;
    
    if (transformationType === 'FILTER_ROWS') {
      if (!parameters.columnId && !parameters.columnName) {
        console.error('FILTER_ROWS transformation requires columnId or columnName', parameters);
        this.errorMessage = 'Please select a column for filtering';
        isValid = false;
      }
      
      if (parameters.operator !== 'is_null' && parameters.operator !== 'not_null' && !parameters.value) {
        console.error('FILTER_ROWS transformation requires a value for the selected operator', parameters);
        this.errorMessage = 'Please enter a value for filtering';
        isValid = false;
      }
    }
    
    if (transformationType === 'FILL_NULL') {
      if (!parameters.columnId && !parameters.columnName) {
        console.error('FILL_NULL transformation requires columnId or columnName', parameters);
        this.errorMessage = 'Please select a column to fill null values';
        isValid = false;
      }
      
      if (!parameters.fillMode) {
        console.error('FILL_NULL transformation requires fillMode', parameters);
        this.errorMessage = 'Please select a fill mode';
        isValid = false;
      }
      
      if (parameters.fillMode === 'value' && !parameters.fillValue) {
        console.error('FILL_NULL transformation with mode "value" requires fillValue', parameters);
        this.errorMessage = 'Please provide a value to fill nulls with';
        isValid = false;
      }
    }
    
    if (!isValid) {
      return;
    }
    
    // Log the parameters being sent
    console.log(`Sending transformation parameters:`, JSON.stringify(parameters));
    
    this.dataPreparationService.addTransformation(this.preparationId, transformationType, parameters).subscribe({
      next: (response) => {
        console.log('Transformation applied successfully:', response);
        // Refresh the preparation to show the new transformation step
        this.loadPreparation();
        this.showTransformationForm = false;
        this.selectedTransformationType = '';
      },
      error: (err) => {
        console.error('Error applying transformation', err);
        this.errorMessage = err.error?.message || 'Failed to apply transformation. Please try again.';
        
        // Log more detailed information
        if (err.error) {
          console.error('Error details:', err.error);
        }
      }
    });
  }

  executePreparation(): void {
    this.isExecuting = true;
    
    this.dataPreparationService.executePreparation(this.preparationId).subscribe({
      next: (response) => {
        this.isExecuting = false;
        // Reload preparation to show updated status
        this.loadPreparation();
      },
      error: (err) => {
        this.isExecuting = false;
        console.error('Error executing preparation', err);
        this.errorMessage = err.error?.message || 'Failed to execute preparation. Please try again.';
      }
    });
  }

  /**
   * Handle toggling a transformation step's active state
   * @param stepId The ID of the step to toggle
   * @param isActive The current active state to toggle
   */
  toggleTransformationStep(stepId: string, isActive: boolean): void {
    this.dataPreparationService.updateTransformation(
      this.preparationId,
      stepId,
      { active: !isActive }
    ).subscribe({
      next: () => {
        // Refresh preparation and preview
        this.loadPreparation();
      },
      error: (err) => {
        console.error('Error toggling transformation step', err);
      }
    });
  }

  deleteTransformationStep(stepId: string): void {
    if (confirm('Are you sure you want to delete this transformation step?')) {
      this.dataPreparationService.deleteTransformation(this.preparationId, stepId).subscribe({
        next: () => {
          this.loadPreparation();
        },
        error: (err) => {
          console.error('Error deleting transformation step', err);
          this.errorMessage = err.error?.message || 'Failed to delete transformation step. Please try again.';
        }
      });
    }
  }

  /**
   * Handles the reordering of transformation steps
   */
  reorderTransformationSteps(stepIds: string[]): void {
    console.log('Reordering transformation steps:', stepIds);
    
    this.dataPreparationService.reorderTransformations(this.preparationId, stepIds).subscribe({
      next: () => {
        console.log('Transformation steps reordered successfully');
        this.loadPreparation();
      },
      error: (err) => {
        console.error('Error reordering transformation steps', err);
        this.errorMessage = err.error?.message || 'Failed to reorder transformation steps. Please try again.';
      }
    });
  }

  getTransformationTypeLabel(type: string): string {
    const found = this.transformationTypes.find(t => t.value === type);
    return found ? found.label : type;
  }

  formatDate(date: string | Date): string {
    if (!date) return 'N/A';
    return new Date(date).toLocaleString();
  }

  goBackToList(): void {
    this.router.navigate(['/data-preparation']);
  }
} 