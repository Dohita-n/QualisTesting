import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { DatasetSummary } from '../models/dataset-summary.model';
import { ColumnStatistics, ChartDataPoint } from '../../../core/models/column-statistics.model';
import { environment } from '../../../../environements/environment';

@Injectable({
  providedIn: 'root'
})
export class DataProfilerService {
  private baseUrl = environment.apiUrl + '/profiling';

  constructor(private http: HttpClient) {}

  getDatasetSummary(datasetId: string, userId: string): Observable<DatasetSummary> {
    return this.http.get<DatasetSummary>(
      `${this.baseUrl}/datasets/${datasetId}/summary?userId=${userId}`
    ).pipe(
      map(response => this.enrichDatasetSummary(response)),
      catchError(this.handleError)
    );
  }

  getColumnStatistics(datasetId: string, columnId: string, userId: string): Observable<ColumnStatistics> {
    return this.http.get<ColumnStatistics>(
      `${this.baseUrl}/datasets/${datasetId}/columns/${columnId}/statistics?userId=${userId}`
    ).pipe(
      map(response => this.enrichColumnStatistics(response)),
      catchError(this.handleError)
    );
  }

  // Transform raw API data to include derived metrics
  private enrichDatasetSummary(data: any): DatasetSummary {
    const summary = data as DatasetSummary;
    
    // Calculate overall quality score if validation data is available
    // For now just return the raw data
    return summary;
  }

  private enrichColumnStatistics(data: any): ColumnStatistics {
    const stats = data as ColumnStatistics;
    
    // Calculate skewness if mean, median, stdDev are available
    if (stats.mean !== undefined && stats.median !== undefined && 
        stats.stdDev !== undefined && stats.stdDev !== 0) {
      stats.skewness = 3 * (stats.mean - stats.median) / stats.stdDev;
    }
    
    // Transform frequent values for visualization
    if (stats.frequentValues) {
      const total = Object.values(stats.frequentValues).reduce((sum, count) => sum + count, 0);
      stats.valueDistribution = Object.entries(stats.frequentValues).map(([name, count]) => ({
        name,
        value: count,
        percent: (count / total) * 100
      }));
    }
    
    return stats;
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    console.error('API error:', error);
    
    if (error.status === 404) {
      return throwError(() => new Error('Dataset or column not found'));
    }
    
    return throwError(() => new Error('An error occurred while fetching data'));
  }
} 