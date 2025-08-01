import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { environment } from '../../../../environements/environment';
import { Preparation } from '../models/preparation.model';
import { PreparationPreviewDTO } from '../models/preparation-preview.model';
import { AuthService } from '../../../core/services/auth.service';

@Injectable({
  providedIn: 'root'
})
export class DataPreparationService {
  private apiUrl = environment.apiUrl;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) { }

  // Helper method to get auth headers
  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    if (!token) {
      return new HttpHeaders({
        'Content-Type': 'application/json'
      });
    }
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    });
  }

  // Helper method to create params with user ID
  private createUserParams(additionalParams: Record<string, string> = {}): HttpParams | null {
    const currentUserId = this.authService.getCurrentUserId();
    if (!currentUserId) {
      return null;
    }
    
    let params = new HttpParams().set('userId', currentUserId);
    
    // Add any additional parameters
    Object.entries(additionalParams).forEach(([key, value]) => {
      params = params.set(key, value);
    });
    
    return params;
  }

  /**
   * Get all preparations
   */
  getAllPreparations(): Observable<Preparation[]> {
    const params = this.createUserParams();
    
    return this.http.get<Preparation[]>(`${this.apiUrl}/preparations`, { 
      headers: this.getAuthHeaders(),
      params: params || undefined
    }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Get a preparation by ID
   */
  getPreparationById(id: string): Observable<Preparation> {
    const params = this.createUserParams();
    
    return this.http.get<Preparation>(`${this.apiUrl}/preparations/${id}`, {
      headers: this.getAuthHeaders(),
      params: params || undefined
    }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Create a new preparation
   */
  createPreparation(data: {
    name: string;
    description: string;
    datasetId: string;
  }): Observable<Preparation> {
    const params = this.createUserParams();
    
    return this.http.post<Preparation>(`${this.apiUrl}/preparations`, data, {
      headers: this.getAuthHeaders(),
      params: params || undefined
    }).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Add a transformation step to a preparation
   */
  addTransformation(
    preparationId: string,
    transformationType: string,
    parameters: any
  ): Observable<any> {
    // Clone parameters to avoid modifying the original
    const adaptedParams = { ...parameters };
    
    // Adapt parameters based on transformation type
    if (transformationType === 'FILTER_ROWS' || transformationType === 'FILL_NULL') {
      // Based on logs, the backend expects 'column' instead of 'columnId' and 'columnName'
      if (adaptedParams.columnId && adaptedParams.columnName) {
        // Add 'column' parameter using the column name
        adaptedParams.column = adaptedParams.columnName;
      }
    }
    
    // Special handling for FILL_NULL
    if (transformationType === 'FILL_NULL') {
      // Makes sure fillMode is set correctly
      if (!adaptedParams.fillMode) {
        adaptedParams.fillMode = 'value'; // Default
      }
      
      // Remove 'value' if it exists (backend expects fillValue)
      if (adaptedParams.hasOwnProperty('value')) {
        delete adaptedParams.value;
      }
    }
    
    // Create the payload with adapted parameters
    const payload = {
      transformationType: transformationType,
      parameters: adaptedParams
    };
    
    const params = this.createUserParams();
    
    return this.http.post(
      `${this.apiUrl}/preparations/${preparationId}/transformations`,
      payload,
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Get a preview of transformations
   */
  getPreview(preparationId: string): Observable<PreparationPreviewDTO> {
    const params = this.createUserParams();
    
    return this.http.post<PreparationPreviewDTO>(
      `${this.apiUrl}/preparations/${preparationId}/preview`,
      {},
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Update a transformation step
   */
  updateTransformation(
    preparationId: string, 
    stepId: string, 
    changes: any
  ): Observable<any> {
    const params = this.createUserParams();
    
    return this.http.put(
      `${this.apiUrl}/preparations/${preparationId}/transformations/${stepId}`,
      changes,
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Delete a transformation step
   */
  deleteTransformation(preparationId: string, stepId: string): Observable<any> {
    const params = this.createUserParams();
    
    return this.http.delete(
      `${this.apiUrl}/preparations/${preparationId}/transformations/${stepId}`,
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Reorder transformation steps
   */
  reorderTransformations(preparationId: string, stepIds: string[]): Observable<any> {
    const params = this.createUserParams();
    
    return this.http.put(
      `${this.apiUrl}/preparations/${preparationId}/transformations/reorder`,
      { stepIds: stepIds },
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Delete a preparation
   */
  deletePreparation(preparationId: string): Observable<any> {
    const params = this.createUserParams();
    
    return this.http.delete(
      `${this.apiUrl}/preparations/${preparationId}`,
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }

  /**
   * Execute a preparation to create a new dataset
   */
  executePreparation(preparationId: string): Observable<any> {
    const params = this.createUserParams();
    
    return this.http.post(
      `${this.apiUrl}/preparations/${preparationId}/execute`,
      {},
      { 
        headers: this.getAuthHeaders(),
        params: params || undefined 
      }
    ).pipe(
      catchError(this.handleError)
    );
  }
  
  /**
   * Handle HTTP errors
   * @param error The HTTP error response
   * @returns Observable with error message
   */
  private handleError(error: HttpErrorResponse) {
    let errorMessage = '';
    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side error
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
      
      // Try to extract more specific error message if available
      if (error.error && typeof error.error === 'object') {
        if (error.error.message) {
          errorMessage = error.error.message;
        } else if (error.error.error) {
          errorMessage = error.error.error;
        }
      } else if (typeof error.error === 'string') {
        errorMessage = error.error;
      }
    }
    return throwError(() => new Error(errorMessage));
  }
} 