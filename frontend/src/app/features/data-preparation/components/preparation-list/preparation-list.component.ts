import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Preparation } from '../../models';
import { UtilityService } from '../../../../core/services/utility.service';

@Component({
  selector: 'app-preparation-list',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './preparation-list.component.html',
  styleUrls: ['./preparation-list.component.css']
})
export class PreparationListComponent {
  @Input() preparations: Preparation[] = [];
  @Output() preparationSelected = new EventEmitter<Preparation>();
  @Output() preparationDeleted = new EventEmitter<string>();

  constructor(private utilityService: UtilityService) {}

  /**
   * Format date to a user-friendly string
   */
  formatDate(dateInput: string | Date): string {
    return this.utilityService.formatDate(dateInput);
  }

  /**
   * Handle preparation selection
   */
  onSelect(preparation: Preparation): void {
    this.preparationSelected.emit(preparation);
  }

  /**
   * Handle preparation deletion
   */
  onDelete(event: Event, preparationId: string): void {
    event.stopPropagation();
    
    if (confirm(`Are you sure you want to delete this preparation? This action cannot be undone.`)) {
      this.preparationDeleted.emit(preparationId);
    }
  }

  /**
   * Get status class for styling
   */
  getStatusClass(status: string): string {
    switch (status) {
      case 'COMPLETE':
        return 'status-complete';
      case 'PROCESSING':
        return 'status-processing';
      case 'ERROR':
        return 'status-error';
      default:
        return 'status-draft';
    }
  }
} 