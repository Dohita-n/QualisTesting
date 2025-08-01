import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription, of } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';

import { DataProfilerService } from './services/data-profiler.service';
import { ValidationService } from '../../core/services/validation.service';
import { DatasetSummary, DatasetColumn } from './models/dataset-summary.model';
import { ColumnStatistics, ChartDataPoint } from '../../core/models/column-statistics.model';
import { AuthService } from '../../core/services/auth.service';
import { DatasetManagementService } from '../../core/services/dataset-management.service';

import { BarChartComponent } from './components/bar-chart/bar-chart.component';
import { BoxPlotComponent } from './components/box-plot/box-plot.component';
import { SkewnessGaugeComponent } from './components/skewness-gauge/skewness-gauge.component';

@Component({
  selector: 'app-data-profiler',
  templateUrl: './data-profiler.component.html',
  styleUrls: ['./data-profiler.component.css'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    BarChartComponent,
    BoxPlotComponent,
    SkewnessGaugeComponent
  ]
})
export class DataProfilerComponent implements OnInit, OnDestroy {
  // Expose Math to the template
  Math = Math;
  
  datasetId: string = '';
  userId: string = '';
  
  datasetSummary: DatasetSummary | null = null;
  selectedColumn: DatasetColumn | null = null;
  columnStatistics: ColumnStatistics | null = null;
  
  columnFilter: string = '';
  dataTypeFilter: string = '';
  
  loading = {
    summary: false,
    columnDetails: false,
    validation: false
  };
  
  error = {
    summary: '',
    columnDetails: '',
    validation: ''
  };
  
  // Responsive design
  isSmallScreen = false;
  private subscriptions: Subscription[] = [];
  
  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private dataProfilerService: DataProfilerService,
    private validationService: ValidationService,
    private authService: AuthService,
    private datasetManagementService: DatasetManagementService
  ) {}
  
  ngOnInit(): void {
    this.checkScreenSize();
    window.addEventListener('resize', this.onResize.bind(this));
    
    // Get current user ID from auth service
    const currentUserId = this.authService.getCurrentUserId();
    if (!currentUserId) {
      this.error.summary = 'Authentication error: User ID not available';
      return;
    }
    this.userId = currentUserId;
    
    // Subscribe to route params to get datasetId
    const routeSub = this.route.params.subscribe(params => {
      if (params['id']) {
        // If dataset ID is provided in the URL, use it directly
        this.datasetId = params['id'];
        this.loadDatasetSummary();
      } else {
        // Otherwise, try to get the most recent dataset
        this.loadLatestDataset();
      }
    });
    
    this.subscriptions.push(routeSub);
  }
  
  loadLatestDataset(): void {
    this.loading.summary = true;
    this.error.summary = '';
    
    const datasetsSub = this.datasetManagementService.loadUserDatasets()
      .pipe(finalize(() => this.loading.summary = false))
      .subscribe({
        next: (datasets) => {
          if (datasets && datasets.length > 0) {
            // Sort datasets by createdAt date (newest first)
            const sortedDatasets = [...datasets].sort((a, b) => 
              new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
            );
            
            // Use the most recent dataset
            const latestDataset = sortedDatasets[0];
            this.datasetId = latestDataset.id;
            
            // Navigate to the dataset URL to update browser history
            this.router.navigate(['/data-profiler', this.datasetId], { replaceUrl: true });
            
            // Load the dataset summary
            this.loadDatasetSummary();
          } else {
            this.error.summary = 'No datasets found. Please upload a dataset first.';
          }
        },
        error: (err) => {
          this.error.summary = err.message || 'Failed to load datasets';
        }
      });
      
    this.subscriptions.push(datasetsSub);
  }
  
  loadDatasetSummary(): void {
    this.loading.summary = true;
    this.error.summary = '';
    
    this.dataProfilerService.getDatasetSummary(this.datasetId, this.userId)
      .pipe(finalize(() => this.loading.summary = false))
      .subscribe({
        next: (data) => {
          this.datasetSummary = data;
          if (data.columns.length > 0) {
            // Select the first column by default
            this.selectColumn(data.columns[0]);
          }
        },
        error: (err) => {
          this.error.summary = err.message || 'Failed to load dataset summary';
        }
      });
  }
  
  selectColumn(column: DatasetColumn): void {
    this.selectedColumn = column;
    this.loadColumnStatistics(column.id);
  }
  
  loadColumnStatistics(columnId: string): void {
    this.loading.columnDetails = true;
    this.error.columnDetails = '';
    this.columnStatistics = null;
    
    this.dataProfilerService.getColumnStatistics(this.datasetId, columnId, this.userId)
      .pipe(finalize(() => this.loading.columnDetails = false))
      .subscribe({
        next: (data) => {
          this.columnStatistics = data;
          // After loading statistics, load validation data
          this.loadValidationData(columnId);
        },
        error: (err) => {
          this.error.columnDetails = err.message || 'Failed to load column details';
        }
      });
  }
  
  loadValidationData(columnId: string): void {
    this.loading.validation = true;
    this.error.validation = '';
    
    this.validationService.validateColumn(this.datasetId, columnId)
      .pipe(finalize(() => this.loading.validation = false))
      .subscribe({
        next: (data) => {
          if (this.columnStatistics) {
            this.columnStatistics.validationMetrics = data;
          }
        },
        error: (err) => {
          this.error.validation = err.message || 'Failed to load validation data';
        }
      });
  }
  
  calculateQualityScore(): number {
    if (!this.datasetSummary || !this.datasetSummary.rowCount) return 0;
    
    // Calculate overall quality based on all columns
    // This is a simplistic approach - could be refined
    let validTotal = 0;
    let rowsTotal = 0;
    
    this.datasetSummary.columns.forEach(column => {
      if (column.nullCount !== undefined) {
        const nonNullCount = this.datasetSummary!.rowCount - column.nullCount;
        validTotal += nonNullCount;
        rowsTotal += this.datasetSummary!.rowCount;
      }
    });
    
    return rowsTotal > 0 ? (validTotal / rowsTotal) * 100 : 0;
  }
  
  getZScores(columnStats: ColumnStatistics): {value: string, score: number}[] {
    if (!columnStats.frequentValues || !columnStats.mean || !columnStats.stdDev) {
      return [];
    }
    
    return Object.entries(columnStats.frequentValues)
      .map(([value, count]) => {
        // For non-numeric values, we can't calculate z-score
        if (isNaN(Number(value))) {
          return { value, score: 0 };
        }
        
        const numValue = Number(value);
        const zScore = (numValue - columnStats.mean!) / columnStats.stdDev!;
        return { value, score: zScore };
      })
      .sort((a, b) => Math.abs(b.score) - Math.abs(a.score));
  }
  
  get filteredColumns(): DatasetColumn[] {
    if (!this.datasetSummary) return [];
    
    return this.datasetSummary.columns.filter(column => {
      const nameMatches = !this.columnFilter || 
        column.name.toLowerCase().includes(this.columnFilter.toLowerCase());
      const typeMatches = !this.dataTypeFilter || 
        column.dataType === this.dataTypeFilter;
      return nameMatches && typeMatches;
    });
  }
  
  getQualityClass(column: DatasetColumn): string {
    if (!this.datasetSummary) return 'quality-unknown';
    
    const nonNullPercent = this.datasetSummary.rowCount > 0 ? 
      ((this.datasetSummary.rowCount - column.nullCount) / this.datasetSummary.rowCount) * 100 : 0;
    
    if (nonNullPercent > 95) return 'quality-good';
    if (nonNullPercent > 80) return 'quality-warning';
    return 'quality-bad';
  }
  
  getQualityPercent(column: DatasetColumn): number {
    if (!this.datasetSummary || this.datasetSummary.rowCount === 0) return 0;
    
    return ((this.datasetSummary.rowCount - column.nullCount) / this.datasetSummary.rowCount) * 100;
  }
  
  getQualityClassFromMetrics(validPercent: number): string {
    if (validPercent > 95) return 'quality-good';
    if (validPercent > 80) return 'quality-warning';
    return 'quality-bad';
  }
  
  getQualityLabelFromMetrics(validPercent: number): string {
    if (validPercent > 95) return 'Excellent';
    if (validPercent > 80) return 'Good';
    if (validPercent > 60) return 'Fair';
    return 'Poor';
  }
  
  getColumnSummary(column: DatasetColumn): string {
    if (!this.datasetSummary) return '';
    
    if (column.uniqueCount === this.datasetSummary.rowCount) {
      return 'All unique values';
    }
    if (column.uniqueCount === 1) {
      return 'Constant value';
    }
    
    return `${column.uniqueCount} distinct values`;
  }
  
  isNumericColumn(dataType: string): boolean {
    return ['INTEGER', 'FLOAT', 'DECIMAL'].includes(dataType);
  }
  
  toNumber(value: string | undefined): number {
    if (value === undefined) return 0;
    const num = Number(value);
    return isNaN(num) ? 0 : num;
  }
  
  getPatternDescription(dataType: string): string {
    switch(dataType) {
      case 'INTEGER':
        return 'This pattern matches integers with optional sign';
      case 'FLOAT':
      case 'DECIMAL':
        return 'This pattern matches decimal numbers with optional sign';
      case 'DATE':
        return 'This pattern matches date values in standard formats';
      case 'BOOLEAN':
        return 'This pattern matches boolean values (true/false, yes/no, 1/0, t/f)';
      case 'EMAIL':
        return 'This pattern validates email addresses';
      case 'URL':
        return 'This pattern validates URLs';
      default:
        return 'This pattern validates the data format';
    }
  }
  
  getZScorePosition(score: number): number {
    // Convert z-score to a position percentage (0-100) for visualization
    // Limit to -3 to 3 standard deviations
    const limited = Math.max(-3, Math.min(3, score));
    return ((limited + 3) / 6) * 100;
  }
  
  exportReport(): void {
    // TODO: Implement export functionality
    alert('Export functionality will be implemented in a future version.');
  }
  
  // Responsive design methods
  onResize(): void {
    this.checkScreenSize();
  }
  
  checkScreenSize(): void {
    this.isSmallScreen = window.innerWidth < 768;
  }
  
  getChartHeight(): number {
    return this.isSmallScreen ? 200 : 300;
  }
  
  ngOnDestroy(): void {
    window.removeEventListener('resize', this.onResize.bind(this));
    // Unsubscribe from all subscriptions to prevent memory leaks
    this.subscriptions.forEach(sub => sub.unsubscribe());
  }
  
  // Edge case handling
  safeFrequentValues(stats: ColumnStatistics | null): ChartDataPoint[] {
    if (!stats || !stats.frequentValues) return [];
    
    const frequentValues = stats.frequentValues;
    const total = Object.values(frequentValues).reduce((sum, count) => sum + count, 0);
    
    return Object.entries(frequentValues).map(([value, count]) => ({
      name: value,
      value: count,
      percent: total > 0 ? (count / total) * 100 : 0
    }));
  }
  
  get columnNullPercent(): number {
    if (!this.columnStatistics || !this.datasetSummary || this.datasetSummary.rowCount === 0) return 0;
    return (this.columnStatistics.nullCount / this.datasetSummary.rowCount) * 100;
  }
  
  get columnValidationPercent(): number {
    if (!this.columnStatistics?.validationMetrics) return 0;
    const { validCount, invalidCount, emptyCount } = this.columnStatistics.validationMetrics;
    const total = validCount + invalidCount + emptyCount;
    return total > 0 ? (validCount / total) * 100 : 0;
  }
  
  get hasValidationPattern(): boolean {
    return !!this.columnStatistics?.validationMetrics?.pattern;
  }
  
  getValidationPattern(): string {
    if (!this.columnStatistics?.validationMetrics?.pattern) {
      // Return a default pattern based on data type
      return this.getDefaultPatternForType(this.columnStatistics?.dataType || 'STRING');
    }
    return this.columnStatistics.validationMetrics.pattern;
  }
  
  getDefaultPatternForType(dataType: string): string {
    switch(dataType) {
      case 'INTEGER': return '^-?\\d+$';
      case 'FLOAT':
      case 'DECIMAL': return '^-?\\d+(\\.\\d+)?$';
      case 'DATE': return '^\\d{4}-\\d{2}-\\d{2}$';
      case 'BOOLEAN': return '^(true|false|t|f|yes|no|1|0)$';
      case 'EMAIL': return '^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$';
      case 'URL': return '^(https?:\\/\\/)?[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?$';
      default: return '.*';
    }
  }
}
