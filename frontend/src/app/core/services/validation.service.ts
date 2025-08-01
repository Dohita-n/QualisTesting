import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ValidationMetrics } from '../models/column-statistics.model';
import { environment } from '../../../environements/environment';

@Injectable({
  providedIn: 'root'
})
export class ValidationService {
  private baseUrl = environment.apiUrl + '/validation';

  constructor(private http: HttpClient) {}

  validateColumn(datasetId: string, columnId: string, pattern?: string): Observable<ValidationMetrics> {
    return this.http.post<any>(
      `${this.baseUrl}/datasets/${datasetId}/columns/${columnId}/validate`,
      { pattern: pattern }
    ).pipe(
      map(result => this.transformValidationResult(result)),
      catchError(this.handleError)
    );
  }

  private transformValidationResult(result: any): ValidationMetrics {
    const totalCount = result.validCount + result.invalidCount + result.emptyCount;
    
    return {
      validCount: result.validCount || 0,
      invalidCount: result.invalidCount || 0,
      emptyCount: result.emptyCount || 0,
      totalCount,
      validPercent: totalCount > 0 ? (result.validCount / totalCount) * 100 : 0,
      invalidPercent: totalCount > 0 ? (result.invalidCount / totalCount) * 100 : 0,
      emptyPercent: totalCount > 0 ? (result.emptyCount / totalCount) * 100 : 0,
      pattern: result.pattern || '',
      lastValidated: result.lastValidated ? new Date(result.lastValidated) : undefined
    };
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    console.error('Validation error:', error);
    return throwError(() => new Error('An error occurred during validation'));
  }
} 