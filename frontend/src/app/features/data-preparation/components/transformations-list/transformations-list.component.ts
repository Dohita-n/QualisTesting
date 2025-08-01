import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TransformationStep } from '../../models';

@Component({
  selector: 'app-transformations-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transformations-list.component.html',
  styleUrls: ['./transformations-list.component.css']
})
export class TransformationsListComponent {
  @Input() transformations: TransformationStep[] = [];
  @Output() updateTransformation = new EventEmitter<{ stepId: string, changes: Partial<TransformationStep> }>();
  @Output() deleteTransformation = new EventEmitter<string>();
  @Output() reorderTransformations = new EventEmitter<string[]>();
  
  // For drag-and-drop reordering
  draggedIndex: number = -1;
  
  /**
   * Get a human-readable name for a transformation type
   */
  getTransformationName(type: string): string {
    const types: {[key: string]: string} = {
      'FILTER_ROWS': 'Filter',
      'FILL_NULL': 'Fill Null Values',
      'COLUMN_RENAME': 'Rename Column',
      'COLUMN_FORMULA': 'Formula',
      'COLUMN_DROP': 'Drop Column'
    };
    
    return types[type] || type;
  }
  
  /**
   * Get a summary of transformation parameters
   */
   /**
   * Get a summary of transformation parameters
   */
   getTransformationSummary(step: TransformationStep): string {
    switch (step.transformationType) {
      case 'FILTER_ROWS':
        return `${step.parameters.columnName} ${this.getOperatorLabel(step.parameters.operator)} ${step.parameters.value || ''}`;
        
      case 'FILL_NULL':
        return `${step.parameters.columnName} fillMode: ${step.parameters.fillMode}${step.parameters.fillValue ? ' value: ' + step.parameters.fillValue : ''}`;
        
      case 'COLUMN_RENAME':
        return `${step.parameters.columnName} → ${step.parameters.newName}`;

      case 'COLUMN_FORMULA':
        return `${step.parameters.formula} → ${step.parameters.outputColumn}`;
        
      case 'COLUMN_DROP':
        return `${step.parameters.columnName}`;
        
      default:
        return JSON.stringify(step.parameters);
    }
  }
  
   
  /**
   * Get human-readable label for filter operators
   */
  private getOperatorLabel(operator: string): string {
    const operators: {[key: string]: string} = {
      'equals': '=',
      'not_equals': '≠',
      'contains': 'contains',
      'greater_than': '>',
      'less_than': '<',
      'is_null': 'is null',
      'not_null': 'is not null'
    };
    
    return operators[operator] || operator;
  }
  
  /**
   * Handle toggling a transformation's active state
   */
  toggleTransformationActive(stepId: string, isActive: boolean): void {
    this.updateTransformation.emit({
      stepId,
      changes: { active: isActive }
    });
  }
  
  /**
   * Handle deleting a transformation
   */
  onDeleteTransformation(event: Event, stepId: string): void {
    event.stopPropagation();
    
    if (confirm('Are you sure you want to delete this transformation?')) {
      this.deleteTransformation.emit(stepId);
    }
  }
  
  /**
   * Start dragging a transformation
   */
  onDragStart(index: number): void {
    this.draggedIndex = index;
  }
  
  /**
   * Handle drag over event
   */
  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }
  
  /**
   * Handle dropping a transformation to reorder
   */
  onDrop(index: number): void {
    if (this.draggedIndex === -1 || this.draggedIndex === index) {
      return;
    }
    
    // Reorder the transformations
    const reordered = [...this.transformations];
    const [removed] = reordered.splice(this.draggedIndex, 1);
    reordered.splice(index, 0, removed);
    
    // Emit the reordered transformation IDs
    this.reorderTransformations.emit(reordered.map(step => step.id));
    
    // Reset drag state
    this.draggedIndex = -1;
  }
  
  /**
   * Handle drag end event
   */
  onDragEnd(): void {
    this.draggedIndex = -1;
  }
  
  /**
   * Get transformation icon based on type
   */
  getTransformationIcon(type: string): string {
    const icons: {[key: string]: string} = {
      'FILTER_ROWS': 'filter',
      'FILL_NULL': 'fill',
      'COLUMN_RENAME': 'rename',
      'COLUMN_FORMULA': 'formula',
      'COLUMN_DROP': 'drop'
    };
    
    return icons[type] || 'transform';
  }
}
