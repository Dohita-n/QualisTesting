import { 
  Component, Input, OnChanges, SimpleChanges, ElementRef, 
  ViewChild, OnInit, AfterViewInit, NgZone, Output, 
  EventEmitter, OnDestroy, ChangeDetectionStrategy, ChangeDetectorRef 
} from '@angular/core';
import { CommonModule } from '@angular/common';
// import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { DataService } from '../../services/data.service';
import { ColumnTypeChange as CoreColumnTypeChange } from '../../../core/models/column-type-change.model';
import { Subscription, Subject, fromEvent } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { debounceTime, throttleTime, takeUntil } from 'rxjs/operators';
import { ColumnValidation } from '../../../features/datasets/dataset-view/dataset-view.component';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { FormsModule } from '@angular/forms';

// Add interfaces for better type safety
interface ColumnMetadata {
  id: string;
  name: string;
  inferredDataType: string;
  decimalPrecision?: number;
  decimalScale?: number;
}

interface ValidationStats {
  cached: number;
  validated: number;
  empty: number;
  invalid: number;
}

interface IframeMessage {
  action: string;
  columnName?: string;
  newType?: string;
  precision?: number;
  scale?: number;
  scrollX?: number;
  scrollY?: number;
  show?: boolean;
  currentType?: string;
}

@Component({
  selector: 'app-iframe-table',
  standalone: true,
  imports: [CommonModule,FormsModule],
  templateUrl: './iframe-table.component.html',
  styleUrls: ['./iframe-table.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush // Use OnPush for better performance
})
export class IframeTableComponent implements OnChanges, OnInit, AfterViewInit, OnDestroy {
  @Input() headers: string[] = [];
  @Input() data: any[] = [];
  @Input() maxHeight: string = '600px';
  @Input() datasetId: string | null = '';
  @Input() userId: string | null = '';
  @Input() validationResults: { [columnId: string]: ColumnValidation } = {};
  @Output() typeChangesSaved = new EventEmitter<void>();
  @Output() pendingChangesUpdated = new EventEmitter<number>();
  @Output() dataChanged = new EventEmitter<any[]>();
  @Output() rowModified = new EventEmitter<any>();


  
  //  @ViewChild('iframeElement') iframeElement!: ElementRef<HTMLIFrameElement>;
  message: string = '';
  messageType: 'success' | 'error' = 'success';
  searchTerm: string = '';
  // tableHtml: SafeHtml = '';
  iframeHeight: number = 400;
  columnTypes: {[key: string]: string} = {};
  columnMap: Map<string, ColumnMetadata> = new Map();
  pendingTypeChanges: Map<string, CoreColumnTypeChange> = new Map();
  availableDataTypes: string[] = ['STRING', 'INTEGER', 'FLOAT', 'DATE', 'BOOLEAN', 'DECIMAL'];
  hasChanges: boolean = false;
  activeColumn: string = '';
  scrollX: number = 0;
  scrollY: number = 0;
  
  // Cache-related properties
  private templateCache: {[key: string]: string} = {};
  private headerHtmlCache: {[key: string]: string} = {}; 
  private rowHtmlCache: {[key: string]: string} = {};
  private tableVersion: number = 0;
  private dataHash: string = '';
  
  // Observable management
  private destroy$ = new Subject<void>();
  private templateSubscription: Subscription | null = null;
  private template: string = '';
  private resizeObserver: ResizeObserver | null = null;
  
  // Validation caching
  private validationRegexCache: { [key: string]: RegExp } = {};
  private validationResultCache: { [key: string]: { [value: string]: string } } = {};
  private validationDebouncer: Subject<void> = new Subject<void>();
  private isInitialRender: boolean = false;
  private isValidationInProgress: boolean = false;
  private readonly LARGE_DATASET_THRESHOLD = 1000;
  private validationStats: ValidationStats = { cached: 0, validated: 0, empty: 0, invalid: 0 };
  
  // Add a WeakMap to store resize observers by iframe element
  private _iframeResizeObservers = new WeakMap<HTMLIFrameElement, ResizeObserver>();
  
  constructor(
    // private sanitizer: DomSanitizer, 
    private ngZone: NgZone,
    private dataService: DataService,
    private http: HttpClient,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private notificationService: NotificationService
  ) {}
  
  ngOnInit(): void {
    this.loadAvailableDataTypes();
    
    // Set up the message listener for iframe communication with better cleanup
    fromEvent<MessageEvent>(window, 'message')
      .pipe(takeUntil(this.destroy$))
      .subscribe(this.handleIframeMessages.bind(this));
    
    // Load the HTML template
    this.loadTemplate();
    
    // Setup debouncing for validation in large datasets
    this.validationDebouncer
      .pipe(
        debounceTime(100),
        takeUntil(this.destroy$)
      )
      .subscribe(() => {
        this.triggerValidation();
      });
       this.userId = this.authService.getCurrentUserId();

  if (!this.userId) {
    alert('Utilisateur non connecté ou ID manquant');
  }
  }
  
  ngAfterViewInit(): void {
    // Use ResizeObserver instead of event listener for better performance
    this.setupResizeObserver();
    
    // We need to ensure that column types are loaded before generating the HTML
    // If we already have column types, generate the table immediately
    if (Object.keys(this.columnTypes).length > 0) {
      console.log('Column types already available, generating table');
      setTimeout(() => this.generateTableHtml(), 0);
    } else if (this.datasetId) {
      // If we have a dataset ID but no column types, fetch them first
      console.log('Fetching column types before generating table');
      this.fetchColumnTypes();
    } else {
      // No dataset ID, just generate the table with what we have
      console.log('No dataset ID, generating table with available data');
      setTimeout(() => this.generateTableHtml(), 0);
    }
  }

  // Set up a ResizeObserver for more efficient size tracking
  private setupResizeObserver(): void {
    if ('ResizeObserver' in window) {
      this.resizeObserver = new ResizeObserver((entries: ResizeObserverEntry[]) => {
        // Use our throttle wrapper for the callback execution
        const throttledResize = throttle(() => {
          this.ngZone.run(() => {
            // this.adjustIframeHeight();
            this.cdr.markForCheck();
          });
        }, 100);
        
        // Execute the throttled function
        throttledResize();
      });
      
      // if (this.iframeElement?.nativeElement) {
      //   this.resizeObserver.observe(this.iframeElement.nativeElement);
      // }
    } else {
      // Fallback to window resize event
      fromEvent(window, 'resize')
        .pipe(
          throttleTime(100),
          takeUntil(this.destroy$)
        )
        .subscribe(() => {
          this.ngZone.run(() => {
            // this.adjustIframeHeight();
            this.cdr.markForCheck();
          });
        });
    }
  }
  
  ngOnChanges(changes: SimpleChanges): void {
    // Calculate a hash for data to detect real changes
    const newDataHash = this.calculateDataHash();
    const dataReallyChanged = this.dataHash !== newDataHash;
    
    if (dataReallyChanged) {
      this.dataHash = newDataHash;
      this.tableVersion++;
      this.clearCaches();
    }
    
    if (changes['datasetId'] && this.datasetId && this.headers.length > 0) {
      this.fetchColumnTypes();
      this.clearValidationCaches();
    } else if ((changes['headers'] || changes['data']) && dataReallyChanged) {
      this.generateTableHtml();
      this.clearValidationCaches();
      // setTimeout(() => this.adjustIframeHeight(), 0);
    } else if (changes['validationResults']) {
      this.clearValidationCaches();
      this.generateTableHtml();
    }
  }

  // Calculate a hash for the data to detect real changes
  private calculateDataHash(): string {
    if (!this.data || this.data.length === 0) return 'empty';
    
    // Use the first and last row as a fingerprint along with the length
    // This is more efficient than checking the entire dataset
    const firstRow = JSON.stringify(this.data[0] || {});
    const lastRow = JSON.stringify(this.data[this.data.length - 1] || {});
    return `${this.data.length}:${firstRow.length}:${lastRow.length}`;
  }
  
  // Clear all caches when data fundamentally changes
  private clearCaches(): void {
    this.headerHtmlCache = {};
    this.rowHtmlCache = {};
    this.clearValidationCaches();
  }
  
  loadAvailableDataTypes(): void {
    this.dataService.getAvailableDataTypes().subscribe(types => {
      this.availableDataTypes = types;
      this.generateTableHtml();
      this.cdr.markForCheck();
    });
  }
editValue(rowIndex: number, col: string, newVal: string): void {
  // Vérifier les permissions avant d'autoriser l'édition
  if (!this.hasEditPermission()) {
    this.notificationService.showError('Vous n\'avez pas les permissions nécessaires pour modifier ces données');
    return;
  }

  this.data[rowIndex][col] = newVal;
  this.data[rowIndex]._modified = true;
  this.dataChanged.emit(this.data); // Émet toutes les données de la page modifiées
}

/**
 * Vérifie si l'utilisateur a les permissions d'édition
 */
private hasEditPermission(): boolean {
  return this.authService.hasRole('EDIT_DATA') || this.authService.hasRole('ADMIN');
}



  isMatch(value: any): boolean {
    if (!this.searchTerm || !value) return false;
    return String(value).toLowerCase().includes(this.searchTerm.toLowerCase());
  }
  handleIframeMessages(event: MessageEvent): void {
    try {
      const data = JSON.parse(event.data) as IframeMessage;
      
      this.ngZone.run(() => {
        switch(data.action) {
          case 'showDataTypeDropdown':
            // this.saveScrollPosition();
            this.activeColumn = data.columnName || '';
            
            // // Instead of regenerating the whole table, just show the dropdown
            // this.updateColumnTypeDropdown(data.columnName || '', data.currentType || '');
            break;
            
          case 'changeDataType':
            // this.saveScrollPosition();
            this.handleDataTypeChange(
              data.columnName || '', 
              data.newType || '', 
              data.precision, 
              data.scale
            );
            // setTimeout(() => this.restoreScrollPosition(), 0);
            break;
            
          case 'applyChanges':
            this.applyChanges();
            break;
            
          case 'closeDropdown':
            // this.saveScrollPosition();
            this.activeColumn = '';
            // this.closeDropdown();
            break;
            
          case 'scrollChanged':
            this.scrollX = data.scrollX || 0;
            this.scrollY = data.scrollY || 0;
            break;
            
          case 'showValidationIndicator':
          case 'hideTooltips':
            // Just passing through these events
            break;
        }
      });
    } catch (e) {
      console.error('Error handling iframe message:', e);
    }
  }

  // // New method to update just the dropdown without regenerating everything
  // private updateColumnTypeDropdown(columnName: string, currentType: string): void {
  //   try {
  //     const iframe = this.iframeElement?.nativeElement;
  //     if (!iframe?.contentDocument) return;
      
  //     // Find the column header
  //     const th = Array.from(iframe.contentDocument.querySelectorAll('th')).find(el => 
  //       el.textContent?.includes(columnName)
  //     );
      
  //     if (!th) return;
      
  //     // Find or create the dropdown
  //     let dropdown = th.querySelector('.column-type-dropdown');
  //     if (!dropdown) {
  //       dropdown = document.createElement('div');
  //       dropdown.className = 'column-type-dropdown';
  //       th.appendChild(dropdown);
  //     } else {
  //       // If dropdown exists, just make it visible
  //       (dropdown as HTMLElement).style.display = 'block';
  //       return;
  //     }
      
  //     // Generate dropdown content
  //     const decimalSettings = this.getDecimalSettings(columnName);
      
  //     // Create dropdown content
  //     dropdown.innerHTML = `
  //       ${this.availableDataTypes.map(type => `
  //         <div class="column-type-dropdown-item" 
  //             onclick="changeDataType('${this.escapeHtml(columnName)}', '${type}')">
  //           ${type}
  //         </div>
  //       `).join('')}
        
  //       ${currentType === 'DECIMAL' || this.availableDataTypes.includes('DECIMAL') ? `
  //         <div class="decimal-settings" id="decimalSettings-${this.escapeHtml(columnName)}">
  //           <div class="decimal-settings-row">
  //             <span class="decimal-label">Precision:</span>
  //             <input type="number" id="precision-${this.escapeHtml(columnName)}" class="decimal-spinner" 
  //                   min="1" max="38" value="${decimalSettings.precision}" 
  //                   ${currentType !== 'DECIMAL' ? 'disabled' : ''}>
  //           </div>
  //           <div class="decimal-settings-row">
  //             <span class="decimal-label">Scale:</span>
  //             <input type="number" id="scale-${this.escapeHtml(columnName)}" class="decimal-spinner" 
  //                   min="0" max="38" value="${decimalSettings.scale}" 
  //                   ${currentType !== 'DECIMAL' ? 'disabled' : ''}>
  //           </div>
  //           <p class="decimal-info">Precision must be >= Scale</p>
  //           <button class="decimal-apply" 
  //                   onclick="applyDecimalSettings('${this.escapeHtml(columnName)}')"
  //                   ${currentType !== 'DECIMAL' ? 'disabled' : ''}>
  //             Apply Settings
  //           </button>
  //         </div>
  //       ` : ''}
  //     `;
      
  //     // Set up decimal spinner behavior
  //     const decimalSpinners = dropdown.querySelectorAll('.decimal-spinner');
  //     decimalSpinners.forEach((spinner: Element) => {
  //       spinner.addEventListener('input', this.handleDecimalSpinnerInput.bind(this));
  //     });
      
  //     // Restore scroll position
  //     this.restoreScrollPosition();
  //   } catch (e) {
  //     console.error('Error updating column type dropdown:', e);
  //     // Fallback to regenerating the whole table
  //     this.generateTableHtml(columnName);
  //   }
  // }
  
  // Handle decimal spinner input
  private handleDecimalSpinnerInput(event: Event): void {
    const spinner = event.target as HTMLInputElement;
    const id = spinner.id;
    const columnName = id.split('-')[1];
    const isScale = id.startsWith('scale');
    
    // Cross-validate precision and scale
    if (isScale) {
      const precisionInput = document.getElementById('precision-' + columnName) as HTMLInputElement;
      const scale = parseInt(spinner.value) || 0;
      const precision = parseInt(precisionInput.value) || 10;
      
      if (scale > precision) {
        precisionInput.value = scale.toString();
      }
    } else {
      const scaleInput = document.getElementById('scale-' + columnName) as HTMLInputElement;
      const precision = parseInt(spinner.value) || 10;
      const scale = parseInt(scaleInput.value) || 0;
      
      if (precision < scale) {
        spinner.value = scale.toString();
      }
    }
  }
  
  // Close the dropdown without regenerating the table
  // private closeDropdown(): void {
  //   try {
  //     const iframe = this.iframeElement?.nativeElement;
  //     if (!iframe?.contentDocument) return;
      
  //     const dropdowns = iframe.contentDocument.querySelectorAll('.column-type-dropdown');
  //     dropdowns.forEach(dropdown => {
  //       (dropdown as HTMLElement).style.display = 'none';
  //     });
      
  //     this.activeColumn = '';
  //     this.restoreScrollPosition();
  //   } catch (e) {
  //     console.error('Error closing dropdown:', e);
  //     // Fallback to regenerating the table
  //     this.generateTableHtml();
  //   }
  // }
  
  // Utility throttle function
  private throttle(fn: Function, delay: number): Function {
    let lastCall = 0;
    return function(...args: any[]) {
      const now = Date.now();
      if (now - lastCall < delay) return;
      lastCall = now;
      return fn(...args);
    };
  }
  
  fetchColumnTypes(): void {
    if (!this.datasetId || this.headers.length === 0) {
      console.warn('No dataset ID or headers available');
      return;
    }
    
    console.log(`Fetching column types for dataset: ${this.datasetId}`);
    const datasetId = this.datasetId;
    
    // Check if we already have the column types cached
    const cacheKey = `columns-${datasetId}-${this.tableVersion}`;
    if (this.templateCache[cacheKey]) {
      console.log('Using cached column data');
      // Parse cached data
      try {
        const cachedData = JSON.parse(this.templateCache[cacheKey]);
        this.columnMap = this.mapToColumnMetadata(cachedData);
        this.updateColumnTypesFromMap();
        console.log('Column types loaded from cache:', this.columnTypes);
        this.generateTableHtml();
        return;
      } catch (e) {
        console.warn('Error parsing cached column data, fetching fresh data', e);
      }
    }
    
    console.log('Fetching fresh column data from API');
    this.dataService.getDatasetColumns(datasetId).subscribe({
      next: (columns) => {
        console.log(`Received ${columns.length} columns from API`);
        
        if (columns.length === 0) {
          console.warn('No columns returned from API');
          this.generateTableHtml();
          return;
        }
        
        // Log first few columns for debugging
        const sampleColumns = columns.slice(0, 3);
        console.log('Sample columns:', sampleColumns);
        
        // Cache the column data
        this.templateCache[cacheKey] = JSON.stringify(columns);
        
        // Process each column to get its type using our mapper
        this.columnMap = this.mapToColumnMetadata(columns);
        this.updateColumnTypesFromMap();
        
        console.log('Column types loaded from API:', this.columnTypes);
        this.generateTableHtml();
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error fetching column types:', error);
        // On error, generate table with existing column types
        this.generateTableHtml();
      }
    });
  }
  
  // Extract column types from map to reduce redundant code
  private updateColumnTypesFromMap(): void {
    const previousTypes = { ...this.columnTypes };
    const hasChanged = { value: false };
    
    this.headers.forEach(header => {
      const column = this.columnMap.get(header);
      const newType = column?.inferredDataType || 'UNKNOWN';
      
      // Only update if the type has changed
      if (this.columnTypes[header] !== newType) {
        this.columnTypes[header] = newType;
        hasChanged.value = true;
      }
    });
    
    // Log if column types have changed
    if (hasChanged.value) {
      console.log('Column types updated:', this.columnTypes);
      // Force change detection
      this.cdr.markForCheck();
    }
  }

  // Fix the type mismatch by adapting the interface or adding a type mapper
  // Let's assume DatasetColumn has properties like id, name, etc. but might be missing inferredDataType
  private mapToColumnMetadata(columns: any[]): Map<string, ColumnMetadata> {
    return new Map(columns.map(col => [
      col.name, 
      {
        id: col.id,
        name: col.name,
        inferredDataType: col.dataType || col.inferredDataType || 'UNKNOWN',
        decimalPrecision: col.decimalPrecision,
        decimalScale: col.decimalScale
      }
    ]));
  }

  // onIframeLoad(): void {
  //   console.log('Iframe loaded - initializing');
    
  //   // If we have column types but they're not displayed, regenerate the table
  //   const iframe = this.iframeElement?.nativeElement;
  //   const hasColumnTypes = Object.keys(this.columnTypes).length > 0;
  //   const typesNotDisplayed = hasColumnTypes && 
  //     iframe?.contentDocument?.querySelectorAll('.column-type').length === 0;
    
  //   if (typesNotDisplayed) {
  //     console.log('Column types loaded but not displayed, regenerating table');
  //     // Force table regeneration with a small delay
  //     setTimeout(() => {
  //       this.generateTableHtml();
  //       this.adjustIframeHeight();
  //     }, 100);
  //   } else {
  //     // Regular initialization
  //     setTimeout(() => {
  //       this.adjustIframeHeight();
  //       this.saveScrollPosition();
  //       this.setupIframeEventListeners();
  //       this.logDataTypesDebug();
  //     }, 100);
  //   }
  // }

  // saveScrollPosition(): void {
  //   try {
  //     const iframe = this.iframeElement?.nativeElement;
  //     if (!iframe || !iframe.contentWindow || !iframe.contentDocument) return;
      
  //     const iframeDoc = iframe.contentDocument;
  //     const container = iframeDoc.querySelector('.table-container');
  //     if (container) {
  //       this.scrollX = container.scrollLeft;
  //       this.scrollY = container.scrollTop;
  //     }
  //   } catch (e) {
  //     console.error('Error saving scroll position:', e);
  //   }
  // }

  // restoreScrollPosition(): void {
  //   try {
  //     const iframe = this.iframeElement?.nativeElement;
  //     if (!iframe || !iframe.contentWindow || !iframe.contentDocument) return;
      
  //     const iframeDoc = iframe.contentDocument;
  //     const container = iframeDoc.querySelector('.table-container');
  //     if (container) {
  //       // Use requestAnimationFrame for smoother scrolling
  //       requestAnimationFrame(() => {
  //         container.scrollLeft = this.scrollX;
  //         container.scrollTop = this.scrollY;
  //       });
  //     }
  //   } catch (e) {
  //     console.error('Error restoring scroll position:', e);
  //   }
  // }

  handleDataTypeChange(columnName: string, newType: string, precision?: number, scale?: number): void {
    // Vérifier les permissions avant d'autoriser le changement de type
    if (!this.hasEditPermission()) {
      this.notificationService.showError('Vous n\'avez pas les permissions nécessaires pour modifier ces données');
      return;
    }

    const column = this.columnMap.get(columnName);
    if (!column) return;
    
    // Don't add to pending changes if setting to current type
    const originalType = this.getOriginalColumnType(columnName);
    if (newType === originalType && !this.pendingTypeChanges.has(column.id)) {
      // Remove from pending changes if it exists and we're setting back to original
      if (this.pendingTypeChanges.has(column.id)) {
        this.pendingTypeChanges.delete(column.id);
      }
      
      // Reset the display type
      this.columnTypes[columnName] = originalType;
      
      // Reset active column
      this.activeColumn = '';
      
      // Update hasChanges flag
      this.hasChanges = this.pendingTypeChanges.size > 0;
      
      // Notify parent about changes
      this.pendingChangesUpdated.emit(this.pendingTypeChanges.size);
      
      // Update the column type display without regenerating the entire table
      // this.updateColumnTypeDisplay(columnName, originalType);
      return;
    }
    
    let change: CoreColumnTypeChange = {
      columnId: column.id,
      newDataType: newType
    };
    
    // If the new type is DECIMAL, add precision and scale
    if (newType === 'DECIMAL') {
      change.decimalPrecision = precision !== undefined ? precision : column.decimalPrecision || 10;
      change.decimalScale = scale !== undefined ? scale : column.decimalScale || 2;
    }
    
    // Add to pending changes
    this.pendingTypeChanges.set(column.id, change);
    
    // Update local column types to reflect the change in UI
    this.columnTypes[columnName] = newType;
    
    // Update the hasChanges flag
    this.hasChanges = this.pendingTypeChanges.size > 0;
    
    // Notify parent about changes
    this.pendingChangesUpdated.emit(this.pendingTypeChanges.size);
    
    // Reset active column
    this.activeColumn = '';
    
    // Update just the specific column type display instead of regenerating the entire table
    // this.updateColumnTypeDisplay(columnName, newType, precision, scale);
  }

  // New method to update just the column type display without regenerating the entire table
  // private updateColumnTypeDisplay(columnName: string, newType: string, precision?: number, scale?: number): void {
  //   try {
  //     const iframe = this.iframeElement?.nativeElement;
  //     if (!iframe?.contentDocument) return;
      
  //     // Find the column header
  //     const th = Array.from(iframe.contentDocument.querySelectorAll('th')).find(el => 
  //       el.textContent?.includes(columnName)
  //     );
      
  //     if (!th) return;
      
  //     // Find the column type span
  //     const columnTypeSpan = th.querySelector('.column-type');
  //     if (!columnTypeSpan) return;
      
  //     // Update the column type display
  //     const isPending = this.columnMap.has(columnName) && 
  //       this.pendingTypeChanges.has(this.columnMap.get(columnName)!.id);
      
  //     // Update class to show it's changed
  //     if (isPending) {
  //       columnTypeSpan.classList.add('changed');
  //     } else {
  //       columnTypeSpan.classList.remove('changed');
  //     }
      
  //     // Update the text content
  //     let displayText = newType;
  //     if (newType === 'DECIMAL' && precision !== undefined && scale !== undefined) {
  //       displayText = `${newType} (${precision},${scale})`;
  //     } else if (newType === 'DECIMAL') {
  //       const settings = this.getDecimalSettings(columnName);
  //       displayText = `${newType} (${settings.precision},${settings.scale})`;
  //     }
      
  //     columnTypeSpan.textContent = displayText;
      
  //     // Close any open dropdowns
  //     this.closeDropdown();
  //   } catch (e) {
  //     console.error('Error updating column type display:', e);
  //     // Fallback to regenerating the table
  //     this.generateTableHtml();
  //   }
  // }

  // Get the original data type for a column (before any pending changes)
  getOriginalColumnType(columnName: string): string {
    const column = this.columnMap.get(columnName);
    if (column) {
      return column.inferredDataType || 'UNKNOWN';
    }
    return 'UNKNOWN';
  }

  // Get the precision and scale values for a column
  getDecimalSettings(columnName: string): { precision: number, scale: number } {
    const column = this.columnMap.get(columnName);
    if (column) {
      const columnId = column.id;
      if (this.pendingTypeChanges.has(columnId)) {
        const change = this.pendingTypeChanges.get(columnId);
        if (change?.newDataType === 'DECIMAL') {
          return {
            precision: change.decimalPrecision || 10,
            scale: change.decimalScale || 2
          };
        }
      }
      
      return {
        precision: column.decimalPrecision || 10,
        scale: column.decimalScale || 2
      };
    }
    
    return { precision: 10, scale: 2 };
  }

  applyChanges(): void {
    // Vérifier les permissions avant d'autoriser l'application des changements
    if (!this.hasEditPermission()) {
      this.notificationService.showError('Vous n\'avez pas les permissions nécessaires pour modifier ces données');
      return;
    }

    if (!this.datasetId || this.pendingTypeChanges.size === 0) return;
    
    const changes = Array.from(this.pendingTypeChanges.values());
    
    // Validate DECIMAL types have precision and scale
    const invalidChanges = changes.filter(change => 
      change.newDataType === 'DECIMAL' && 
      (change.decimalPrecision === undefined || change.decimalScale === undefined)
    );
    
    if (invalidChanges.length > 0) {
      console.error('Decimal types require both precision and scale values');
      return;
    }
    
    this.dataService.applyDataTypeChanges(this.datasetId, changes).subscribe({
      next: () => {
        // Clear pending changes
        this.pendingTypeChanges.clear();
        this.hasChanges = false;
        this.activeColumn = '';
        
        // Increment table version to invalidate caches
        this.tableVersion++;
        
        // Emit event to notify parent component
        this.typeChangesSaved.emit();
        this.pendingChangesUpdated.emit(0);
        
        // Refresh the column types and regenerate the table
        this.fetchColumnTypes();
      },
      error: (error) => {
        console.error('Failed to apply data type changes:', error);
      }
    });
  }

  // Generate validation bar HTML for a column
  getValidationBarHtml(columnName: string): string {
    const column = this.columnMap.get(columnName);
    if (!column || !this.validationResults || !this.validationResults[column.id]) {
      return '';
    }
    
    const validation = this.validationResults[column.id];
    const total = validation.result.validCount + validation.result.invalidCount + validation.result.emptyCount;
    
    if (total === 0) return '';
    
    const validPercent = (validation.result.validCount / total) * 100;
    const invalidPercent = (validation.result.invalidCount / total) * 100;
    const emptyPercent = (validation.result.emptyCount / total) * 100;
    
    return `
    <div class="validation-bar-container">
      <span class="validation-label">Validation:</span>
      <div class="validation-bar">
        <div class="validation-segment-valid" style="width: ${validPercent}%"></div>
        <div class="validation-segment-invalid" style="width: ${invalidPercent}%"></div>
        <div class="validation-segment-empty" style="width: ${emptyPercent}%"></div>
      </div>
      <div class="validation-stats">
        <span class="stat valid">Valid: ${validPercent.toFixed(1)}%</span>
        <span class="stat invalid">Invalid: ${invalidPercent.toFixed(1)}%</span>
        <span class="stat empty">Empty: ${emptyPercent.toFixed(1)}%</span>
      </div>
    </div>
    `;
  }
  
  // private onWindowResize(): void {
  //   this.ngZone.run(() => {
  //     this.adjustIframeHeight();
  //   });
  // } 
  
//  private adjustIframeHeight(): void {
//     if (!this.iframeElement || !this.iframeElement.nativeElement) {
//       console.warn('No iframe element available');
//       return;
//     }
    
//     const iframe = this.iframeElement.nativeElement;
    
//     try {
//       // Verify we have access to the iframe document
//       if (!iframe.contentDocument && !iframe.contentWindow?.document) {
//         console.warn('Cannot access iframe document - it may not be loaded yet');
//         return;
//       }
      
//       const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
//       if (!iframeDoc) return;
      
//       // Additional safety checks for document elements
//       if (!iframeDoc.documentElement || !iframeDoc.body) {
//         console.warn('Iframe document is not fully initialized yet');
//         return;
//       }
      
//       const table = iframeDoc.querySelector('table');
//       if (table) {
//         const tableHeight = table.offsetHeight;
//         const viewportHeight = Math.min(tableHeight + 30, parseInt(this.maxHeight) || 600);
        
//         // Only update height if it's significantly different to reduce layout thrashing
//         if (Math.abs(this.iframeHeight - viewportHeight) > 10) {
//           this.iframeHeight = Math.max(viewportHeight, 300);
//           this.cdr.markForCheck();
//         }
        
//         const tableWidth = table.offsetWidth;
//         if (iframe.offsetWidth > 0 && tableWidth > 0) {
//           // Only update width if it's actually different
//           const currentWidth = parseInt(table.style.minWidth || '0');
//           const newWidth = Math.max(tableWidth, iframe.offsetWidth);
          
//           if (Math.abs(currentWidth - newWidth) > 10) {
//             table.style.minWidth = `${newWidth}px`;
//           }
//         }
//       } else {
//         // If no table is found, use document dimensions
//         if (iframeDoc.documentElement && iframeDoc.body) {
//           const scrollHeight = iframeDoc.documentElement.scrollHeight;
//           const bodyHeight = iframeDoc.body.scrollHeight;
//           const newHeight = Math.max(scrollHeight, bodyHeight) + 20;
          
//           // Only update if significantly different
//           if (Math.abs(this.iframeHeight - newHeight) > 10) {
//             this.iframeHeight = newHeight;
//             this.cdr.markForCheck();
//           }
//         }
//       }
//     } catch (e) {
//       console.error('Error adjusting iframe height:', e);
//     }
//   }
  
  private loadTemplate(): void {
    console.log('Loading iframe template');
    
    // Check for cached template first
    if (this.templateCache['base-template']) {
      console.log('Using cached template');
      this.template = this.templateCache['base-template'];
      if (this.headers.length > 0) {
        this.generateTableHtml();
      }
      return;
    }
    
    // Show a loading indicator if available
    const loadingIndicator = document.querySelector('.loading-indicator');
    if (loadingIndicator) {
      (loadingIndicator as HTMLElement).style.display = 'block';
    }
    
    this.templateSubscription = this.http.get('assets/templates/iframe-table.html', { responseType: 'text' })
      .subscribe({
        next: (template) => {
          console.log('Template loaded successfully');
          this.template = template;
          // Cache the template
          this.templateCache['base-template'] = template;
          
          if (this.headers.length > 0) {
            this.generateTableHtml();
          }
          
          // Hide loading indicator
          if (loadingIndicator) {
            (loadingIndicator as HTMLElement).style.display = 'none';
          }
        },
        error: (err) => {
          console.error('Failed to load iframe template:', err);
          // Fallback to empty template if loading fails
          this.template = this.getDefaultTemplate();
          // Cache the fallback template
          this.templateCache['base-template'] = this.template;
          
          if (this.headers.length > 0) {
            this.generateTableHtml();
          }
          
          // Hide loading indicator
          if (loadingIndicator) {
            (loadingIndicator as HTMLElement).style.display = 'none';
          }
        }
      });
  }

  private getDefaultTemplate(): string {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
          /* Basic styling */
          body { font-family: sans-serif; margin: 0; padding: 0; }
          table { width: 100%; border-collapse: collapse; }
          th, td { padding: 8px; border: 1px solid #ddd; }
          th { background: #f2f2f2; }
          
          /* Styles for numbering column */
          th.numbering-column {
            position: sticky;
            left: 0;
            z-index: 20;
            background-color: #f2f2f2;
            border-right: 1px solid #ddd;
            min-width: 50px;
            text-align: center;
            font-weight: 600;
          }
          
          td.numbering-column {
            position: sticky;
            left: 0;
            z-index: 20;
            background-color: white;
            border-right: 1px solid #ddd;
            min-width: 50px;
            text-align: center;
            font-weight: 500;
            color: #374151;
          }
          
          /* Hover effect for numbering column */
          tr:hover td.numbering-column {
            background-color: #f3f4f6 !important;
          }
          
          /* Ensure border is visible with shadow */
          th.numbering-column,
          td.numbering-column {
            box-shadow: 2px 0 4px rgba(0, 0, 0, 0.1);
          }
        </style>
      </head>
      <body>
        <div id="tableContainer" style="width: 100%; overflow-x: auto;">
          <table>
            <thead><tr>{{HEADERS}}</tr></thead>
            <tbody>{{ROWS}}</tbody>
          </table>
        </div>
        <script>{{SCRIPTS}}</script>
      </body>
      </html>
    `;
  }

  // Optimize the validation method with better caching
  private getCellValidationClass(columnName: string, value: any): string {
    // Skip validation during initial render for large datasets
    if (this.isInitialRender && this.data.length > this.LARGE_DATASET_THRESHOLD) {
      return '';
    }
    
    // Quick check for empty values
    if (value === null || value === undefined || value === '' || (typeof value === 'string' && value.trim() === '')) {
      this.validationStats.empty++;
      return 'empty-value';
    }
    
    this.validationStats.validated++;
    
    // Get the column and its validation pattern
    const column = this.columnMap.get(columnName);
    if (!column || !column.id || !this.validationResults || !this.validationResults[column.id]) {
      return '';
    }
    
    const validation = this.validationResults[column.id];
    if (!validation.pattern) {
      return '';
    }
    
    // Convert value to string for validation
    const stringValue = String(value);
    
    // Use cached results if available for this column and value
    if (this.validationResultCache[column.id] && 
        this.validationResultCache[column.id][stringValue] !== undefined) {
      this.validationStats.cached++;
      const cachedResult = this.validationResultCache[column.id][stringValue];
      if (cachedResult === 'invalid-value') {
        this.validationStats.invalid++;
      }
      return cachedResult;
    }
    
    try {
      // Use cached regex if available
      let regex: RegExp;
      if (this.validationRegexCache[column.id]) {
        regex = this.validationRegexCache[column.id];
      } else {
        regex = new RegExp(validation.pattern);
        this.validationRegexCache[column.id] = regex;
      }
      
      // Initialize cache for this column if needed
      if (!this.validationResultCache[column.id]) {
        this.validationResultCache[column.id] = {};
      }
      
      // Validate and cache result
      if (!regex.test(stringValue)) {
        this.validationResultCache[column.id][stringValue] = 'invalid-value';
        this.validationStats.invalid++;
        return 'invalid-value';
      } else {
        this.validationResultCache[column.id][stringValue] = '';
        return '';
      }
    } catch (e) {
      // If regex pattern is invalid, don't apply validation styling
      console.error(`Invalid regex pattern for column ${columnName}:`, e);
      return '';
    }
  }

  // Modify the clearValidationCaches method
  private clearValidationCaches(): void {
    console.log('Clearing validation caches');
    this.validationRegexCache = {};
    this.validationResultCache = {};
    this.validationStats = {
      cached: 0,
      validated: 0,
      empty: 0,
      invalid: 0
    };
  }

  // Optimize HTML generation with caching
  private generateTableHtml(activeColumn: string = ''): void {
    // Reset validation stats
    this.validationStats = {
      cached: 0,
      validated: 0,
      empty: 0,
      invalid: 0
    };
    
    // If activeColumn is provided, use it, otherwise use the stored value
    if (activeColumn) {
      this.activeColumn = activeColumn;
    }
    
    if (!this.template) {
      console.warn('Template not loaded yet - deferring table generation');
      // Try loading the template again
      if (!this.templateSubscription || this.templateSubscription.closed) {
        this.loadTemplate();
      }
      return;
    }
    
    // Log status of column types before generating HTML
    const hasColumnTypes = Object.keys(this.columnTypes).length > 0;
    console.log(`Generating table HTML with column types: ${hasColumnTypes ? 'YES' : 'NO'}`);
    if (hasColumnTypes) {
      console.log('First few column types:', 
        Object.entries(this.columnTypes).slice(0, 3).map(([k, v]) => `${k}: ${v}`).join(', '));
    } else if (this.datasetId && this.headers.length > 0) {
      console.warn('Column types missing but dataset ID available, fetching types');
      // If we have a dataset ID but no column types, try fetching them again
      this.fetchColumnTypes();
      return;
    }
    
    // Create a cache key based on current state
    const headerCacheKey = `${this.tableVersion}-${this.activeColumn}-${this.pendingTypeChanges.size}`;
    const rowCacheKey = `${this.tableVersion}-${this.isInitialRender ? 'initial' : 'validated'}`;
    
    // For large datasets, determine whether this is initial render or validation phase
    const isLargeDataset = this.data.length > this.LARGE_DATASET_THRESHOLD;
    
    // Set the initial render flag if this is the first pass for large dataset
    // and we're not currently in validation phase
    if (isLargeDataset && !this.isValidationInProgress) {
      this.isInitialRender = true;
    }
    
    // Generate or retrieve cached header content
    let headerContent: string;
    if (this.headerHtmlCache[headerCacheKey] && hasColumnTypes) {
      headerContent = this.headerHtmlCache[headerCacheKey];
    } else {
      headerContent = this.generateHeaderContent();
      this.headerHtmlCache[headerCacheKey] = headerContent;
    }
    
    // Generate or retrieve cached row content
    let rowsContent: string;
    if (this.rowHtmlCache[rowCacheKey] && !this.isValidationInProgress) {
      rowsContent = this.rowHtmlCache[rowCacheKey];
    } else {
      rowsContent = this.generateRowsContent();
      this.rowHtmlCache[rowCacheKey] = rowsContent;
    }
    
    // Generate JS scripts - this isn't cached because it contains dynamic scroll positions
    const scriptContent = this.generateScriptContent();
    
    // Merge template with content
    let html = this.template;
    html = html.replace('{{HEADERS}}', headerContent);
    html = html.replace('{{ROWS}}', rowsContent);
    html = html.replace('{{SCRIPTS}}', scriptContent);
    html = html.replace('{{SCROLL_X}}', this.scrollX.toString());
    html = html.replace('{{SCROLL_Y}}', this.scrollY.toString());
    
    // this.tableHtml = this.sanitizer.bypassSecurityTrustHtml(html);
    this.cdr.markForCheck();

    // For large datasets, queue validation after initial render
    if (this.isInitialRender && !this.isValidationInProgress) {
      console.log('Queuing delayed validation for large dataset with ' + this.data.length + ' rows');
      this.validationDebouncer.next();
    }
  }
  
  // Extract header content generation to a separate method
  private generateHeaderContent(): string {
    // First check if we have column types
    const hasColumnTypes = Object.keys(this.columnTypes).length > 0;
    if (!hasColumnTypes && this.headers.length > 0) {
      console.warn('Generating headers without column types - they may not display correctly');
    }
    
    // Add the numbering column header first
    let headerContent = `
      <th class="numbering-column">
        #
      </th>
    `;
    
    // Add the rest of the headers
    headerContent += this.headers.map(header => {
      // Get the column type, with extra logging
      const columnType = this.columnTypes[header] || 'UNKNOWN';
      if (columnType === 'UNKNOWN' && this.datasetId) {
        console.warn(`Column type for "${header}" is UNKNOWN despite having datasetId`);
      }
      
      const isPending = this.columnMap.has(header) && 
        this.pendingTypeChanges.has(this.columnMap.get(header)!.id);
      
      // Get decimal settings
      const decimalSettings = this.getDecimalSettings(header);

      // Get validation bar HTML
      const validationBarHtml = this.getValidationBarHtml(header);
      
      if (this.activeColumn === header) {
        return `
          <th>
            ${this.escapeHtml(header)}
            <span class="column-type ${isPending ? 'changed' : ''}" 
                  onclick="selectDataType('${this.escapeHtml(header)}', '${columnType}')">
              ${columnType}${columnType === 'DECIMAL' ? 
                ` (${decimalSettings.precision},${decimalSettings.scale})` : ''}
            </span>
            ${validationBarHtml}
            <div class="column-type-dropdown">
              ${this.availableDataTypes.map(type => `
                <div class="column-type-dropdown-item" 
                    onclick="changeDataType('${this.escapeHtml(header)}', '${type}')">
                  ${type}
                </div>
              `).join('')}
              
              ${columnType === 'DECIMAL' || this.availableDataTypes.includes('DECIMAL') ? `
                <div class="decimal-settings" id="decimalSettings-${this.escapeHtml(header)}">
                  <div class="decimal-settings-row">
                    <span class="decimal-label">Precision:</span>
                    <input type="number" id="precision-${this.escapeHtml(header)}" class="decimal-spinner" 
                          min="1" max="38" value="${decimalSettings.precision}" 
                          ${columnType !== 'DECIMAL' ? 'disabled' : ''}>
                  </div>
                  <div class="decimal-settings-row">
                    <span class="decimal-label">Scale:</span>
                    <input type="number" id="scale-${this.escapeHtml(header)}" class="decimal-spinner" 
                          min="0" max="38" value="${decimalSettings.scale}" 
                          ${columnType !== 'DECIMAL' ? 'disabled' : ''}>
                  </div>
                  <p class="decimal-info">Precision must be >= Scale</p>
                  <button class="decimal-apply" 
                          onclick="applyDecimalSettings('${this.escapeHtml(header)}')"
                          ${columnType !== 'DECIMAL' ? 'disabled' : ''}>
                    Apply Settings
                  </button>
                </div>
              ` : ''}
            </div>
          </th>
        `;
      } else {
        return `
          <th>
            ${this.escapeHtml(header)}
            <span class="column-type ${isPending ? 'changed' : ''}" 
                  onclick="selectDataType('${this.escapeHtml(header)}', '${columnType}')">
              ${columnType}${columnType === 'DECIMAL' ? 
                ` (${decimalSettings.precision},${decimalSettings.scale})` : ''}
            </span>
            ${validationBarHtml}
          </th>
        `;
      }
    }).join('');
    
    return headerContent;
  }
  
  // Extract rows content generation to a separate method
  private generateRowsContent(): string {
    if (this.data.length === 0) {
      return `<tr><td colspan="${this.headers.length + 1}" style="text-align: center; padding: 20px;">No data available</td></tr>`;
    }
    
    // For very large datasets, limit the initial render to improve performance
    const rowsToRender = this.isInitialRender && this.data.length > 1000 ? 
      this.data.slice(0, 1000) : this.data;
    
    return rowsToRender.map((row, index) => `
      <tr>
        <!-- Numbering column -->
        <td class="numbering-column">
          ${index + 1}
        </td>
        ${this.headers.map(header => {
          const value = row[header] || '';
          const validationClass = this.getCellValidationClass(header, value);
          return `<td title="${this.escapeHtml(String(value))}" class="${validationClass}">${this.escapeHtml(String(value))}</td>`;
        }).join('')}
      </tr>
    `).join('');
  }
  
  // Extract script content generation to a separate method
  private generateScriptContent(): string {
    return `
      function selectDataType(columnName, currentType) {
        event.stopPropagation();
        const msg = {
          action: 'showDataTypeDropdown',
          columnName: columnName,
          currentType: currentType
        };
        window.parent.postMessage(JSON.stringify(msg), '*');
      }
      
      function changeDataType(columnName, newType) {
        event.stopPropagation();
        
        // Enable/disable decimal settings if needed
        if (newType === 'DECIMAL') {
          const decimalSettings = document.getElementById('decimalSettings-' + columnName);
          if (decimalSettings) {
            const precisionInput = document.getElementById('precision-' + columnName);
            const scaleInput = document.getElementById('scale-' + columnName);
            const applyBtn = decimalSettings.querySelector('.decimal-apply');
            
            if (precisionInput) precisionInput.disabled = false;
            if (scaleInput) scaleInput.disabled = false;
            if (applyBtn) applyBtn.disabled = false;
          }
        }
        
        // For non-DECIMAL types, send the change immediately
        if (newType !== 'DECIMAL') {
          const msg = {
            action: 'changeDataType',
            columnName: columnName,
            newType: newType
          };
          window.parent.postMessage(JSON.stringify(msg), '*');
        }
      }
      
      function applyDecimalSettings(columnName) {
        event.stopPropagation();
        const precisionInput = document.getElementById('precision-' + columnName);
        const scaleInput = document.getElementById('scale-' + columnName);
        
        if (!precisionInput || !scaleInput) return;
        
        let precision = parseInt(precisionInput.value);
        let scale = parseInt(scaleInput.value);
        
        // Validate inputs
        if (isNaN(precision) || precision < 1) precision = 10;
        if (isNaN(scale) || scale < 0) scale = 2;
        if (precision > 38) precision = 38;
        if (scale > 38) scale = 38;
        if (scale > precision) scale = precision;
        
        // Update input values to show validated values
        precisionInput.value = precision;
        scaleInput.value = scale;
        
        const msg = {
          action: 'changeDataType',
          columnName: columnName,
          newType: 'DECIMAL',
          precision: precision,
          scale: scale
        };
        window.parent.postMessage(JSON.stringify(msg), '*');
      }
      
      function applyChanges() {
        const msg = {
          action: 'applyChanges'
        };
        window.parent.postMessage(JSON.stringify(msg), '*');
      }
      
      // Initialize table behavior
      document.addEventListener('DOMContentLoaded', function() {
        const container = document.getElementById('tableContainer');
        if (container) {
          // Set initial scroll position
          container.scrollLeft = ${this.scrollX};
          container.scrollTop = ${this.scrollY};
          
          // Track scroll changes
          let scrollTimeout;
          container.addEventListener('scroll', function() {
            // Throttle scroll events for better performance
            if (!scrollTimeout) {
              scrollTimeout = setTimeout(function() {
                const scrollMsg = {
                  action: 'scrollChanged',
                  scrollX: container.scrollLeft,
                  scrollY: container.scrollTop
                };
                window.parent.postMessage(JSON.stringify(scrollMsg), '*');
                scrollTimeout = null;
              }, 100);
            }
          });
        }
        
        // Set up validated inputs for decimal spinners
        document.querySelectorAll('.decimal-spinner').forEach(function(spinner) {
          spinner.addEventListener('input', function() {
            const id = this.id;
            const columnName = id.split('-')[1];
            const isScale = id.startsWith('scale');
            
            // Cross-validate precision and scale
            if (isScale) {
              const precisionInput = document.getElementById('precision-' + columnName);
              const scale = parseInt(this.value) || 0;
              const precision = parseInt(precisionInput.value) || 10;
              
              if (scale > precision) {
                precisionInput.value = scale;
              }
            } else {
              const scaleInput = document.getElementById('scale-' + columnName);
              const precision = parseInt(this.value) || 10;
              const scale = parseInt(scaleInput.value) || 0;
              
              if (precision < scale) {
                this.value = scale;
              }
            }
          });
        });

        // Show tooltips on validation bar hover
        document.querySelectorAll('.validation-bar').forEach(function(bar) {
          bar.addEventListener('mouseenter', function() {
            const tooltip = this.nextElementSibling;
            if (tooltip && tooltip.classList.contains('validation-tooltip')) {
              tooltip.style.display = 'block';
              
              // Position the tooltip
              const barRect = this.getBoundingClientRect();
              const tooltipWidth = tooltip.offsetWidth || 200;
              
              // Ensure tooltip is fully visible within viewport
              const leftPosition = Math.max(0, Math.min(
                barRect.left + (barRect.width/2) - (tooltipWidth/2),
                window.innerWidth - tooltipWidth - 10
              ));
              
              tooltip.style.left = leftPosition + 'px';
              tooltip.style.top = (barRect.bottom + 5) + 'px';
              tooltip.style.zIndex = '1000';
            }
          });
          
          bar.addEventListener('mouseleave', function() {
            const tooltip = this.nextElementSibling;
            if (tooltip && tooltip.classList.contains('validation-tooltip')) {
              // Add a small delay before hiding to allow moving to the tooltip
              setTimeout(() => {
                // Only hide if mouse isn't over the tooltip
                if (!tooltip.matches(':hover')) {
                  tooltip.style.display = '';
                }
              }, 100);
            }
          });
          
          // Add event listener for the tooltip itself
          const tooltip = bar.nextElementSibling;
          if (tooltip && tooltip.classList.contains('validation-tooltip')) {
            tooltip.addEventListener('mouseleave', function() {
              this.style.display = '';
            });
          }
        });
      });
      
      // Handle clicks outside dropdown to close it
      document.addEventListener('click', function(evt) {
        const dropdowns = document.querySelectorAll('.column-type-dropdown');
        const columnTypes = document.querySelectorAll('.column-type');
        
        // Check if click is outside dropdown and column type elements
        let outsideClick = true;
        for (const dropdown of dropdowns) {
          if (dropdown.contains(evt.target)) {
            outsideClick = false;
            break;
          }
        }
        
        for (const type of columnTypes) {
          if (type.contains(evt.target)) {
            outsideClick = false;
            break;
          }
        }
        
        if (outsideClick && dropdowns.length > 0) {
          window.parent.postMessage(JSON.stringify({ action: 'closeDropdown' }), '*');
        }
      });
      
      // Add window message listener for validation indicator
      window.addEventListener('message', function(event) {
        try {
          const message = JSON.parse(event.data);
          if (message.action === 'showValidationIndicator') {
            showValidationIndicator(message.show);
          }
        } catch (e) {
          // Ignore parsing errors for non-JSON messages
        }
      });
      
      // Function to show validation indicator
      function showValidationIndicator(show) {
        let indicator = document.getElementById('validationIndicator');
        if (!indicator && show) {
          indicator = document.createElement('div');
          indicator.id = 'validationIndicator';
          indicator.className = 'validation-indicator';
          indicator.innerHTML = 'Applying validation...';
          document.body.appendChild(indicator);
          
          // Auto-hide after 3 seconds to avoid stuck indicator
          setTimeout(() => {
            const element = document.getElementById('validationIndicator');
            if (element) element.style.display = 'none';
          }, 3000);
        } else if (indicator) {
          indicator.style.display = show ? 'block' : 'none';
        }
      }
    `;
  }
  
  private escapeHtml(unsafe: string): string {
    return unsafe
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
  
  ngOnDestroy(): void {
    // Clean up destroy subject
    this.destroy$.next();
    this.destroy$.complete();
    
    // Clean up subscriptions
    if (this.templateSubscription) {
      this.templateSubscription.unsubscribe();
    }
    
    // Clean up ResizeObserver
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
    
    // Clean up iframe content resize observers
    // if (this._iframeResizeObservers && this.iframeElement?.nativeElement) {
    //   const observer = this._iframeResizeObservers.get(this.iframeElement.nativeElement);
    //   if (observer) {
    //     observer.disconnect();
    //     this._iframeResizeObservers.delete(this.iframeElement.nativeElement);
    //   }
    // }
    
    // Clean up iframe events if possible
    // try {
    //   const iframe = this.iframeElement?.nativeElement;
    //   if (iframe && iframe.contentWindow) {
    //     iframe.contentWindow.removeEventListener('resize', this.onWindowResize.bind(this));
    //   }
    // } catch (e) {
    //   // Ignore errors during cleanup
    // }
    
    // Clean up the validation debouncer
    this.validationDebouncer.complete();
  }

  // Add this method to trigger validation for large datasets
  private triggerValidation(): void {
    // Only trigger if we're in initial render state
    if (!this.isInitialRender) return;
    
    console.log('Triggering validation phase for large dataset');
    
    // Update flags
    this.isInitialRender = false;
    this.isValidationInProgress = true;
    
    // Show validation indicator
    // this.ngZone.run(() => {
    //   setTimeout(() => {
    //     const iframe = this.iframeElement?.nativeElement;
    //     if (iframe && iframe.contentWindow) {
    //       iframe.contentWindow.postMessage(JSON.stringify({
    //         action: 'showValidationIndicator',
    //         show: true
    //       }), '*');
    //     }
    //   }, 50);
    // });
    
    // Queue the validation with requestAnimationFrame for better performance
    requestAnimationFrame(() => {
      // Clear row cache to force re-validation
      const cacheKey = `${this.tableVersion}-validated`;
      delete this.rowHtmlCache[cacheKey];
      
      // Generate table again but this time with validation
      this.generateTableHtml();
      
      // Update flags
      this.isValidationInProgress = false;
      
      // Hide validation indicator after a delay
      // setTimeout(() => {
      //   const iframe = this.iframeElement?.nativeElement;
      //   if (iframe && iframe.contentWindow) {
      //     iframe.contentWindow.postMessage(JSON.stringify({
      //       action: 'showValidationIndicator',
      //       show: false
      //     }), '*');
      //   }
        
      //   // Log validation stats
      //   console.log('Validation complete. Stats:', {
      //     validated: this.validationStats.validated,
      //     cached: this.validationStats.cached,
      //     empty: this.validationStats.empty,
      //     invalid: this.validationStats.invalid,
      //     total: this.data.length * this.headers.length
      //   });
      // }, 100);
    });
  }

  // Add a method to setup event listeners in the iframe
  // private setupIframeEventListeners(): void {
  //   try {
  //     const iframe = this.iframeElement?.nativeElement;
  //     if (!iframe || !iframe.contentWindow || !iframe.contentDocument) return;
      
  //     // If ResizeObserver is available, use it to monitor iframe content size
  //     if (window.ResizeObserver && iframe.contentDocument.body) {
  //       const contentResizeObserver = new ResizeObserver(() => {
  //         this.ngZone.run(() => {
  //           this.adjustIframeHeight();
  //         });
  //       });
        
  //       contentResizeObserver.observe(iframe.contentDocument.body);
        
  //       // Store the observer for cleanup using a safer approach
  //       // Use a WeakMap to store resize observers by iframe element
  //       if (!this._iframeResizeObservers) {
  //         this._iframeResizeObservers = new WeakMap<HTMLIFrameElement, ResizeObserver>();
  //       }
  //       this._iframeResizeObservers.set(iframe, contentResizeObserver);
  //     }
  //   } catch (e) {
  //     console.error('Error setting up iframe event listeners:', e);
  //   }
  // }

  // Debug method to log data type loading info
  private logDataTypesDebug(): void {
    console.log('Data types debug:');
    console.log('- Available types:', this.availableDataTypes);
    console.log('- Column types:', this.columnTypes);
    console.log('- Column map size:', this.columnMap.size);
    
    // Log the first few entries in the column map
    if (this.columnMap.size > 0) {
      const entries = Array.from(this.columnMap.entries()).slice(0, 3);
      console.log('- Sample column map entries:', entries);
    }
  }
}

// Utility function for throttling (moved outside class to be reusable)
function throttle(fn: Function, delay: number): Function {
  let lastCall = 0;
  return function(this: any, ...args: any[]) {
    const now = Date.now();
    if (now - lastCall < delay) return;
    lastCall = now;
    return fn.apply(this, args);
  };
} 