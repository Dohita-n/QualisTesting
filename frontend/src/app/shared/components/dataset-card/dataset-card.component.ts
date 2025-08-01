import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Dataset } from '../../../core/models/dataset.model';
import { UtilityService } from '../../../core/services/utility.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-dataset-card',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dataset-card.component.html',
  styleUrls: ['./dataset-card.component.css']
})
export class DatasetCardComponent {
  @Input() dataset!: Dataset;
  @Output() view = new EventEmitter<Dataset>();
  @Output() delete = new EventEmitter<{dataset: Dataset, event: Event}>();
  @Output() statistics = new EventEmitter<Dataset>();
  @Output() edit = new EventEmitter<{dataset: Dataset, newName: string, event: Event}>();
  
  isEditing = false;
  editedName = '';
  
  constructor(
    private utilityService: UtilityService,
    public authService: AuthService
  ) {}
  
  /**
   * Format date to a user-friendly string
   */
  formatDate(dateInput: string | Date): string {
    return this.utilityService.formatDate(dateInput);
  }
  
  /**
   * Handle view dataset button click
   */
  onViewClick(event: Event): void {
    event.stopPropagation();
    this.view.emit(this.dataset);
  }
  
  /**
   * Handle delete dataset button click
   */
  onDeleteClick(event: Event): void {
    event.stopPropagation();
    this.delete.emit({dataset: this.dataset, event});
  }
  
  /**
   * Handle statistics button click
   */
  onStatisticsClick(event: Event): void {
    event.stopPropagation();
    this.statistics.emit(this.dataset);
  }
  
  /**
   * Start editing dataset name
   */
  startEditing(event: Event): void {
    event.stopPropagation();
    this.editedName = this.dataset.name;
    this.isEditing = true;
  }
  
  /**
   * Save edited dataset name
   */
  saveEdit(event: Event): void {
    event.stopPropagation();
    if (this.editedName.trim() && this.editedName !== this.dataset.name) {
      this.edit.emit({
        dataset: this.dataset,
        newName: this.editedName.trim(),
        event
      });
    }
    this.isEditing = false;
  }
  
  /**
   * Cancel editing
   */
  cancelEdit(event: Event): void {
    event.stopPropagation();
    this.isEditing = false;
  }
  
  /**
   * Check if user has edit permission
   */
  hasEditPermission(): boolean {
    return this.authService.hasRole('EDIT_DATA') || this.authService.hasRole('ADMIN');
  }
} 