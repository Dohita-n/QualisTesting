import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Dataset } from '../../../../core/models/dataset.model';
import { TransformationStep } from '../../models';

@Component({
  selector: 'app-transformation-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './transformation-form.component.html',
  styleUrls: ['./transformation-form.component.css']
})
export class TransformationFormComponent implements OnInit {
  @Input() columns: any[] = [];
  @Input() transformationType: string = '';
  @Input() transformations: TransformationStep[] = [];
  @Output() applyTransformation = new EventEmitter<any>();
  @Output() cancel = new EventEmitter<void>();
  @Output() updateTransformation = new EventEmitter<{ stepId: string, changes: Partial<TransformationStep> }>();
  @Output() deleteTransformation = new EventEmitter<string>();
  @Output() reorderTransformations = new EventEmitter<string[]>();

  transformationForm: FormGroup;
  isLoading = false;
  
  // For drag-and-drop reordering
  draggedIndex: number = -1;
  
  // Transformation types - these match the backend API
  readonly transformationTypes = [
    { value: 'FILTER_ROWS', label: 'Filter Rows' },
    { value: 'FILL_NULL', label: 'Fill Null Values' },
    { value: 'COLUMN_RENAME', label: 'Rename Column' },
    { value: 'COLUMN_DROP', label: 'Drop Column' },
    { value: 'COLUMN_FORMULA', label: 'Formula Calculation' }
  ];
  
  // Operators for filter transformations
  readonly filterOperators = [
    { value: 'greater_than', label: 'Greater Than' },
    { value: 'less_than', label: 'Less Than' },
    { value: 'equals', label: 'Equals' },
    { value: 'not_equals', label: 'Not Equals' },
    { value: 'contains', label: 'Contains' },
    { value: 'not_null', label: 'Not Null' },
    { value: 'is_null', label: 'Is Null' }
  ];
  
  // Fill modes for null value transformations
  readonly fillModes = [
    { value: 'value', label: 'Custom Value' },
    { value: 'mean', label: 'Mean' },
    { value: 'median', label: 'Median' },
    { value: 'most_frequent', label: 'Most Frequent Value' },
    { value: 'zero', label: 'Zero' },
    { value: 'empty_string', label: 'Empty String' }
  ];
  
  // Formula operators
  readonly formulaOperators = [
    { value: '+', label: 'Addition (+)' },
    { value: '-', label: 'Subtraction (-)' },
    { value: '*', label: 'Multiplication (*)' },
    { value: '/', label: 'Division (/)' }
  ];

  constructor(private fb: FormBuilder) {
    this.transformationForm = this.fb.group({
      columnId: ['', Validators.required],
      columnName: [''],
      // For COLUMN_RENAME
      newName: [''],
      // For FILL_NULL
      fillMode: ['value'],
      fillValue: [''],
      // For FILTER_ROWS
      operator: ['equals'],
      value: [''],
      // For COLUMN_FORMULA
      formula: [''],
      outputColumn: [''],
      selectedColumn: [''],
      selectedOperator: ['+'],
      operandValue: ['']
    });
  }

  ngOnInit(): void {
    this.initializeForm();
    
    // Listen for changes to the fillMode and update validators
    this.transformationForm.get('fillMode')?.valueChanges.subscribe(mode => {
      this.onFillModeChange(mode);
    });
    
    // Listen for changes to the operator and update validators
    this.transformationForm.get('operator')?.valueChanges.subscribe(operator => {
      this.onOperatorChange(operator);
    });
  }
  
  // Initialize form based on transformation type
  initializeForm(): void {
    this.transformationForm.reset({
      columnId: '',
      columnName: ''
    });
    
    this.clearAllValidators();
    
    switch (this.transformationType) {
      case 'FILTER_ROWS':
        this.transformationForm.get('operator')?.setValue('equals');
        this.transformationForm.get('value')?.setValue('');
        
        // Add validators
        this.transformationForm.get('columnId')?.setValidators([Validators.required]);
        this.transformationForm.get('operator')?.setValidators([Validators.required]);
        
        // Only set value validator if operator requires a value
        const opValue = this.transformationForm.get('operator')?.value;
        if (opValue !== 'is_null' && opValue !== 'not_null') {
          this.transformationForm.get('value')?.setValidators([Validators.required]);
        } else {
          this.transformationForm.get('value')?.clearValidators();
        }
        break;
        
      case 'FILL_NULL':
        this.transformationForm.get('fillMode')?.setValue('value');
        this.transformationForm.get('fillValue')?.setValue('');
        
        // Add validators
        this.transformationForm.get('columnId')?.setValidators([Validators.required]);
        this.transformationForm.get('fillMode')?.setValidators([Validators.required]);
        
        // Only set fillValue validator if fillMode is 'value'
        if (this.transformationForm.get('fillMode')?.value === 'value') {
          this.transformationForm.get('fillValue')?.setValidators([Validators.required]);
        } else {
          this.transformationForm.get('fillValue')?.clearValidators();
        }
        break;
        
      case 'COLUMN_RENAME':
        this.transformationForm.get('newName')?.setValue('');
        
        // Add validators
        this.transformationForm.get('columnId')?.setValidators([Validators.required]);
        this.transformationForm.get('newName')?.setValidators([Validators.required]);
        break;
        
      case 'COLUMN_FORMULA':
        this.transformationForm.get('formula')?.setValue('');
        this.transformationForm.get('outputColumn')?.setValue('');
        this.transformationForm.get('selectedOperator')?.setValue('+');
        this.transformationForm.get('operandValue')?.setValue('');
        
        // Add validators
        this.transformationForm.get('outputColumn')?.setValidators([Validators.required]);
        this.transformationForm.get('selectedColumn')?.setValidators([Validators.required]);
        break;
    }
    
    // Update form validation state
    this.transformationForm.updateValueAndValidity();
  }

  onColumnChange(): void {
    const columnId = this.transformationForm.get('columnId')?.value;
    if (columnId) {
      const selectedColumn = this.columns.find(col => col.id === columnId);
      if (selectedColumn) {
        this.transformationForm.get('columnName')?.setValue(selectedColumn.name);
      } else {
        this.transformationForm.get('columnName')?.setValue('');
      }
    } else {
      this.transformationForm.get('columnName')?.setValue('');
    }
  }
  
  updateFormula(): void {
    const selectedColumn = this.transformationForm.get('selectedColumn')?.value;
    const selectedOperator = this.transformationForm.get('selectedOperator')?.value;
    const operandValue = this.transformationForm.get('operandValue')?.value;
    
    if (selectedColumn && selectedOperator && operandValue) {
      const columnName = this.columns.find(col => col.id === selectedColumn)?.name || '';
      const formula = `${columnName} ${selectedOperator} ${operandValue}`;
      this.transformationForm.get('formula')?.setValue(formula);
    }
  }
  
  // Prepare form data for submission
  prepareFormData(): any {
    const formValue = this.transformationForm.value;
    const parameters: any = {};
    
    // Always include column information - use columnId and columnName, not "column"
    if (formValue.columnId) {
      parameters.columnId = formValue.columnId;
      
      // Find the column by ID to ensure we have the name
      const selectedColumn = this.columns.find(col => col.id === formValue.columnId);
      if (selectedColumn) {
        parameters.columnName = selectedColumn.name;
      } else {
        // Use the form value as a fallback
        parameters.columnName = formValue.columnName || '';
      }

      // Make sure we don't accidentally include a 'column' property
      if (parameters.hasOwnProperty('column')) {
        delete parameters.column;
      }
    }
    
    switch (this.transformationType) {
      case 'FILTER_ROWS':
        parameters.operator = formValue.operator;
        
        // Only include value if operator is not is_null or not_null
        if (formValue.operator !== 'is_null' && formValue.operator !== 'not_null') {
          parameters.value = formValue.value;
        }
        
        break;
        
      case 'FILL_NULL':
        // Ensure fillMode is properly set
        parameters.fillMode = formValue.fillMode;
        
        // Add fillValue only when fillMode is 'value'
        if (formValue.fillMode === 'value') {
          parameters.fillValue = formValue.fillValue;
        }
        
        break;
        
      case 'COLUMN_RENAME':
        parameters.newName = formValue.newName;
        break;
        
      case 'COLUMN_FORMULA':
        parameters.formula = formValue.formula;
        parameters.outputColumn = formValue.outputColumn;
        break;
    }
    
    // Final check to ensure no 'column' property exists
    if (parameters.hasOwnProperty('column')) {
      delete parameters.column;
    }
    
    return parameters;
  }
  
  onSubmit(): void {
    if (this.transformationForm.invalid) {
      // Mark all fields as touched to show validation errors
      Object.keys(this.transformationForm.controls).forEach(key => {
        const control = this.transformationForm.get(key);
        control?.markAsTouched();
      });
      return;
    }
    
    const parameters = this.prepareFormData();
    this.applyTransformation.emit({
      type: this.transformationType,
      parameters
    });
  }
  
  onCancel(): void {
    this.cancel.emit();
  }
  
  // Check if a field is required based on the current transformation type
  isFieldRequired(fieldName: string): boolean {
    switch (this.transformationType) {
      case 'FILTER_ROWS':
        return fieldName === 'columnId' || 
               (fieldName === 'value' && 
                this.transformationForm.get('operator')?.value !== 'is_null' && 
                this.transformationForm.get('operator')?.value !== 'not_null');
      case 'FILL_NULL':
        return fieldName === 'columnId' || 
               (fieldName === 'fillValue' && this.transformationForm.get('fillMode')?.value === 'value');
      case 'COLUMN_RENAME':
        return fieldName === 'columnId' || fieldName === 'newName';
      case 'COLUMN_DROP':
        return fieldName === 'columnId';
      case 'COLUMN_FORMULA':
        return fieldName === 'formula' || fieldName === 'outputColumn';
      default:
        return false;
    }
  }
  
  /**
   * Handle changes to the fill mode for FILL_NULL transformation
   */
  onFillModeChange(mode: string): void {
    if (this.transformationType !== 'FILL_NULL') return;
    
    if (mode === 'value') {
      this.transformationForm.get('fillValue')?.setValidators([Validators.required]);
    } else {
      this.transformationForm.get('fillValue')?.clearValidators();
      this.transformationForm.get('fillValue')?.setValue('');
    }
    
    this.transformationForm.get('fillValue')?.updateValueAndValidity();
  }
  
  /**
   * Handle changes to the operator for FILTER_ROWS transformation
   */
  onOperatorChange(operator: string): void {
    if (this.transformationType !== 'FILTER_ROWS') return;
    
    if (operator !== 'is_null' && operator !== 'not_null') {
      this.transformationForm.get('value')?.setValidators([Validators.required]);
    } else {
      this.transformationForm.get('value')?.clearValidators();
      this.transformationForm.get('value')?.setValue('');
    }
    
    this.transformationForm.get('value')?.updateValueAndValidity();
  }
  
  clearAllValidators(): void {
    Object.keys(this.transformationForm.controls).forEach(key => {
      const control = this.transformationForm.get(key);
      control?.clearValidators();
      control?.setErrors(null);
      control?.updateValueAndValidity();
    });
  }

  /**
   * Get a human-readable name for a transformation type
   */
  getTransformationName(type: string): string {
    const types: {[key: string]: string} = {
      'FILTER_ROWS': 'Filter Rows',
      'FILL_NULL': 'Fill Null Values',
      'COLUMN_RENAME': 'Rename Column',
      'COLUMN_FORMULA': 'Formula Calculation',
      'COLUMN_DROP': 'Drop Column'
    };
    
    return types[type] || type;
  }
  
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
