import { HttpClient, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, map, of, tap, throwError, switchMap, firstValueFrom, Subject, timer, delay, retryWhen, concatMap, EMPTY, takeUntil, take, filter, timeout } from 'rxjs';
import { environment } from '../../../environements/environment';
import { Dataset, DatasetColumn, DatasetRow, DatasetSummary } from '../models/dataset.model';
import { File as FileModel } from '../models/file.model';
import { AuthService } from '../services/auth.service';

// Add this interface
export interface ColumnStatistics {
  id: string;
  name: string;
  dataType: string;
  nullCount: number;
  uniqueCount: number;
  min?: string;
  max?: string;
  mean?: number;
  median?: number;
  stdDev?: number;
  frequentValues?: {[key: string]: number};
}

/**
 * Column type change model
 */
export interface ColumnTypeChange {
  columnId: string;
  newDataType: string;
  decimalPrecision?: number;
  decimalScale?: number;
}

/**
 * Response from transformation operations
 */
export interface TransformationResponse {
  transformationId: string;
  transformationType: string;
  changesApplied: number;
  datasetId: string;
  status: string;
}

/**
 * Validation request model
 */
export interface ValidationRequest {
  columnId: string;
  pattern: string;
}

/**
 * Validation result model
 */
export interface ValidationResult {
  columnId: string;
  columnName: string;
  validCount: number;
  invalidCount: number;
  emptyCount: number;
  pattern: string;
}

/**
 * Predefined validation patterns for different data types
 */
export interface ValidationPattern {
  dataType: string;
  name: string;
  pattern: string;
  description: string;
}

@Injectable({
  providedIn: 'root'
})
export class DataService {
  private apiUrl = environment.apiUrl;
  
  // Predefined validation patterns
  private predefinedValidationPatterns: ValidationPattern[] = [
    { 
      dataType: 'INTEGER', 
      name: 'Integer', 
      pattern: '^[-+]?\\d+$',
      description: 'Validates whole numbers with optional sign' 
    },
    { 
      dataType: 'DECIMAL', 
      name: 'Decimal', 
      pattern: '^[-+]?\\d*\\.?\\d+$',
      description: 'Validates decimal numbers with optional sign' 
    },
    { 
      dataType: 'DATE', 
      name: 'Date (YYYY-MM-DD)', 
      pattern: '^(?:\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{4})$',
      description: 'Validates dates in YYYY-MM-DD or YYYY/MM/DD format'
    },
    { 
      dataType: 'BOOLEAN', 
      name: 'Boolean', 
      pattern: '^(true|false|yes|f|t|no|0|1)$',
      description: 'Validates boolean values (true/false/yes/no/1/0)'
    },
    { 
      dataType: 'STRING', 
      name: 'Email', 
      pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$',
      description: 'Validates email addresses'
    },
    { 
      dataType: 'STRING', 
      name: 'Phone Number', 
      pattern: '^[+]?[(]?[0-9]{1,4}[)]?[-\\s.]?[0-9]{1,4}[-\\s.]?[0-9]{1,9}$',
      description: 'Validates international phone numbers'
    },
    { 
      dataType: 'STRING', 
      name: 'URL', 
      pattern: '^(https?:\\/\\/)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*\\/?$',
      description: 'Validates URLs'
    },
    { 
      dataType: 'STRING', 
      name: 'Alpha Only', 
      pattern: '^[a-zA-Z]+$',
      description: 'Validates strings with only alphabetic characters'
    }
  ];
  
  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}
  
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
  
  // Generic method for authenticated GET requests
  private authenticatedGet<T>(url: string, additionalParams: Record<string, string> = {}, defaultValue?: T): Observable<T> {
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return defaultValue !== undefined ? of(defaultValue) : throwError(() => new Error('Authentication required'));
    }

    const params = this.createUserParams(additionalParams);
    if (!params) {
      console.error('Error: No user ID available for authenticated request');
      return defaultValue !== undefined ? of(defaultValue) : throwError(() => new Error('Authentication required'));
    }
    
    // Add authorization header to all requests
    const headers = {
      'Authorization': `Bearer ${token}`
    };
    
    return this.http.get<T>(url, { params, headers }).pipe(
      catchError(error => {
        console.error(`Error fetching data from ${url}`, error);
        return defaultValue !== undefined ? of(defaultValue) : throwError(() => error);
      })
    );
  }
  
  // Generic method for authenticated POST requests
  private authenticatedPost<T>(url: string, body: any, additionalParams: Record<string, string> = {}, defaultValue?: T): Observable<T> {
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return defaultValue !== undefined ? of(defaultValue) : throwError(() => new Error('Authentication required'));
    }

    const headers = {
      'Authorization': `Bearer ${token}`
    };

    return this.http.post<T>(url, body, { headers }).pipe(
      catchError(error => {
        console.error(`Error posting data to ${url}`, error);
        return defaultValue !== undefined ? of(defaultValue) : throwError(() => error);
      })
    );
  }
  
  // File upload methods
  uploadFile(file: Blob, description?: string): Observable<FileModel> {
    const formData = new FormData();
    formData.append('file', file);
    if (description) {
      formData.append('description', description);
    }
    
    return this.authenticatedPost<FileModel>(
      `${this.apiUrl}/files/upload`,
      formData
    ).pipe(
      tap(response => console.log('File uploaded successfully', response)),
      catchError(error => {
        console.error('Error uploading file', error);
        throw error;
      })
    );
  }

  //Usage in metrics
  getAllFiles(): Observable<FileModel[]> {
    return this.http.get<FileModel[]>(`${this.apiUrl}/files`).pipe(
      tap(files => console.log('Files retrieved', files)),
      catchError(error => {
        console.error('Error fetching files', error);
        return of([]);
      })
    );
  }
  
  getFileById(fileId: string): Observable<FileModel> {
    return this.http.get<FileModel>(`${this.apiUrl}/files/${fileId}`).pipe(
      tap(file => console.log('File retrieved', file)),
      catchError(error => {
        console.error('Error fetching file', error);
        throw error;
      })
    );
  }
  
  // Dataset methods
  getAllDatasets(targetUserId?: string, requestedByUserId?: string): Observable<Dataset[]> {
    // Get current user ID if not provided and user is authenticated
    if (!requestedByUserId && this.authService.isAuthenticated()) {
      const currentUserId = this.authService.getCurrentUserId();
      if (currentUserId) {
        requestedByUserId = currentUserId;
      }
    }
    
    // If targetUserId is not provided, use the current user ID
    if (!targetUserId && requestedByUserId) {
      targetUserId = requestedByUserId;
    }
    
    // Ensure we have a userId, otherwise we can't make the request
    if (!targetUserId) {
      console.error('Error: No user ID provided or available for dataset request');
      return of([]);
    }
    
    // Create params with the target user ID
    let params = new HttpParams().set('userId', targetUserId);
    
    // Add requestedByUserId if it's different from the targetUserId
    if (requestedByUserId && requestedByUserId !== targetUserId) {
      params = params.set('requestedByUserId', requestedByUserId);
    }
    
    return this.http.get<Dataset[]>(`${this.apiUrl}/datasets`, { params }).pipe(
      tap(datasets => console.log('Datasets retrieved', datasets)),
      catchError(error => {
        console.error('Error fetching datasets', error);
        return of([]);
      })
    );
  }
  
  // Validation methods
  /**
   * Get predefined validation patterns for a specific data type
   */
  getValidationPatternsForType(dataType: string): ValidationPattern[] {
    return this.predefinedValidationPatterns.filter(pattern => 
      pattern.dataType === dataType || pattern.dataType === 'STRING'
    );
  }
  
  /**
   * Get all predefined validation patterns
   */
  getAllValidationPatterns(): ValidationPattern[] {
    return this.predefinedValidationPatterns;
  }
  
  /**
   * Validate a column using a regex pattern
   * @param datasetId Dataset ID
   * @param columnId Column ID
   * @param pattern Regex pattern to validate against
   * @returns Observable with validation result
   */
  validateColumn(datasetId: string, columnId: string, pattern: string): Observable<ValidationResult> {
    const url = `${this.apiUrl}/validation/datasets/${datasetId}/columns/${columnId}/validate`;
    const params = this.createUserParams({ pattern });
    
    if (!params) {
      return throwError(() => new Error('Authentication required'));
    }
    
    return this.http.post<any>(url, null, { params }).pipe(
      map(response => {
        return {
          columnId,
          columnName: response.column,
          validCount: response.validCount,
          invalidCount: response.invalidCount,
          emptyCount: response.nullCount,
          pattern: response.pattern
        } as ValidationResult;
      }),
      catchError(this.handleError)
    );
  }
  
  /**
   * Get validation information for a column
   * @param datasetId Dataset ID
   * @param columnId Column ID
   * @returns Observable with validation result
   */
  getColumnValidation(datasetId: string, columnId: string): Observable<ValidationResult> {
    const url = `${this.apiUrl}/validation/datasets/${datasetId}/columns/${columnId}`;
    const params = this.createUserParams();
    
    if (!params) {
      return throwError(() => new Error('Authentication required'));
    }
    
    return this.http.get<any>(url, { params }).pipe(
      map(response => {
        return {
          columnId,
          columnName: response.column,
          validCount: response.validCount,
          invalidCount: response.invalidCount,
          emptyCount: response.nullCount,
          pattern: response.pattern
        } as ValidationResult;
      }),
      catchError(this.handleError)
    );
  }
  
  getDatasetById(datasetId: string): Observable<Dataset> {
    return this.authenticatedGet<Dataset>(
      `${this.apiUrl}/datasets/${datasetId}`,
      {},
      null as unknown as Dataset
    ).pipe(
      tap(dataset => console.log('Dataset retrieved', dataset))
    );
  }
  
  getDatasetColumns(datasetId: string): Observable<DatasetColumn[]> {
    return this.authenticatedGet<DatasetColumn[]>(
      `${this.apiUrl}/datasets/${datasetId}/columns`,
      {},
      []
    ).pipe(
      tap(columns => console.log('Dataset columns retrieved', columns))
    );
  }
  
  
  getDatasetRows(datasetId: string, page: number = 0, size: number = 200): Observable<{ 
    content: DatasetRow[], 
    totalPages: number, 
    totalElements: number, 
    currentPage: number 
  }> {
    console.log(`Getting dataset rows for dataset ${datasetId}, page: ${page}, size: ${size}`);
    
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return throwError(() => new Error('Authentication required'));
    }
    
    // Get user ID for params
    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      console.error('Error: No user ID available');
      return throwError(() => new Error('User ID required'));
    }
    
    const params = new HttpParams()
      .set('userId', userId)
      .set('page', page.toString())
      .set('size', size.toString());
    
    const headers = {
      'Authorization': `Bearer ${token}`
    };
    
    console.log(`Making dataset rows request with token: ${token.substring(0, 10)}...`);
    
    return this.http.get<any>(
      `${this.apiUrl}/datasets/${datasetId}/rows`,
      { params, headers }
    ).pipe(
      tap(rows => console.log('Dataset rows response received:', rows)),
      catchError(error => {
        console.error(`Error fetching rows for dataset ${datasetId}:`, error);
        if (error.status === 403) {
          console.error('Authorization issue - token might be invalid or expired');
        }
        throw error;
      })
    );
  }
  
  // Data profiling methods
  getDatasetSummary(datasetId: string): Observable<DatasetSummary> {
    return this.authenticatedGet<DatasetSummary>(
      `${this.apiUrl}/profiling/datasets/${datasetId}/summary`,
      {},
      null as unknown as DatasetSummary
    ).pipe(
      tap(summary => console.log('Dataset summary retrieved', summary))
    );
  }
  
  getColumnStatistics(datasetId: string, columnId: string): Observable<ColumnStatistics> {
    return this.authenticatedGet<ColumnStatistics>(
      `${this.apiUrl}/profiling/datasets/${datasetId}/columns/${columnId}/statistics`,
      {},
      null as unknown as ColumnStatistics
    ).pipe(
      tap(statistics => console.log('Column statistics retrieved', statistics))
    );
  }
  
  analyzeDataset(datasetId: string): Observable<any> {
    return this.authenticatedPost<any>(
      `${this.apiUrl}/profiling/datasets/${datasetId}/analyze`,
      {},
      {},
      null
    ).pipe(
      tap(result => console.log('Dataset analysis started', result))
    );
  }
  
    // Fix the findDatasetByFileId method to use proper endpoints
  findDatasetByFileId(fileId: string): Observable<Dataset> {
    console.log('Finding dataset for fileId:', fileId);
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return throwError(() => new Error('Authentication required'));
    }
    
    // Add auth header directly to ensure it's included
    const headers = {
      'Authorization': `Bearer ${token}`
    };
    
    // Get user ID for params
    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      console.error('Error: No user ID available');
      return throwError(() => new Error('User ID required'));
    }
    
    const params = new HttpParams().set('userId', userId);
    
    // Use the datasets endpoint to get all datasets for this user
    return this.http.get<Dataset[]>(`${this.apiUrl}/datasets`, { 
      params, 
      headers
    }).pipe(
      tap(datasets => console.log('All datasets retrieved:', datasets.length)),
      map(datasets => {
        // Find the dataset that matches the file ID
        console.log('Searching for dataset with fileId:', fileId);
        const dataset = datasets.find(ds => ds.file && ds.file.id === fileId);
        if (!dataset) {
          console.error('No dataset found for fileId:', fileId);
          throw new Error(`No dataset found for file ID: ${fileId}`);
        }
        console.log('Found dataset:', dataset);
        return dataset;
      }),
      catchError(error => {
        console.error(`Error finding dataset for fileId ${fileId}:`, error);
        throw error;
      })
    );
  }
  
  // Add a new method to check file status and wait for processing
  checkFileStatus(fileId: string): Observable<FileModel> {
    console.log(`Checking file status for ID: ${fileId}`);
    
    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      return throwError(() => new Error('User ID required'));
    }
    
    return this.authenticatedGet<FileModel>(
      `${this.apiUrl}/files/${fileId}`,
      {}
    ).pipe(
      tap(file => console.log(`File status: ${file.status} for file ${file.originalName}`))
    );
  }
  
  // Wait for file to be processed - improved for large files
  waitForFileProcessing(fileId: string, maxAttempts: number = 60, delayMs: number = 3000): Observable<FileModel> {
    console.log(`Waiting for file ${fileId} to be processed...`);
    
    // Create a counter that survives between emissions
    let attemptCounter = 0;
    
    // Create an observable that polls the server at regular intervals
    return new Observable<FileModel>(observer => {
      console.log('Starting file status polling...');
      
      // Create interval for checking file status
      const intervalId = setInterval(() => {
        attemptCounter++;
        console.log(`Checking file status (attempt ${attemptCounter}/${maxAttempts})...`);
        
        // Check file status
        this.checkFileStatus(fileId).subscribe({
          next: (file) => {
            console.log(`Poll result: File status is '${file.status}' for ${file.originalName}`);
            
            // If file is processed or uploaded, emit the file and complete
            if (file.status === 'PROCESSED' || file.status === 'UPLOADED') {
              console.log(`File ${fileId} is ready with status: ${file.status}`);
              observer.next(file);
              observer.complete();
              clearInterval(intervalId);
            } 
            // If file has an error, emit error and complete
            else if (file.status === 'ERROR') {
              const errorMsg = `File processing failed: ${file.errorMessage || 'Unknown error'}`;
              console.error(errorMsg);
              observer.error(new Error(errorMsg));
              clearInterval(intervalId);
            }
            // If we've reached max attempts, emit error and complete
            else if (attemptCounter >= maxAttempts) {
              const timeoutMsg = `File processing timed out after ${maxAttempts} attempts`;
              console.error(timeoutMsg);
              observer.error(new Error(timeoutMsg));
              clearInterval(intervalId);
            }
            // Otherwise, continue polling (status is still PROCESSING)
            else {
              console.log(`File ${fileId} still processing (attempt ${attemptCounter}/${maxAttempts})...`);
              // Just continue the interval, no need to do anything else
            }
          },
          error: (error) => {
            console.error('Error checking file status:', error);
            
            // If there's a server error but we haven't reached max attempts, just continue
            if (attemptCounter < maxAttempts) {
              console.log(`Error checking status, will retry (attempt ${attemptCounter}/${maxAttempts})...`);
            } else {
              // If we've reached max attempts, emit error and complete
              observer.error(error);
              clearInterval(intervalId);
            }
          }
        });
      }, delayMs);
      
      // Return a cleanup function
      return () => {
        console.log('Cleaning up file status polling interval');
        clearInterval(intervalId);
      };
    });
  }
  
  // Method removed - dataset is now created immediately during upload
  
  // Updated method to correctly handle the complete data flow
  getDataPreview(fileId: string): Observable<any> {
    console.log(`Getting data preview for fileId: ${fileId}, first checking file status`);
    
    // Step 1: Wait for the file to be fully processed - use the fixed waitForFileProcessing method
    return this.waitForFileProcessing(fileId, 30, 2000).pipe(
      // Only continue if we get a proper file back
      filter(file => !!file && (file.status === 'PROCESSED' || file.status === 'UPLOADED')),
      // Step 2: After file is processed, wait a moment then try to find the dataset
      switchMap(file => {
        console.log(`File ${fileId} is processed, waiting for dataset creation...`);
        
        // Add a deliberate delay to give the backend time to create the dataset
        return timer(3000).pipe(
          switchMap(() => {
            console.log(`Attempting to find dataset for file ${fileId}`);
            
            // Function to get all datasets and find one with matching fileId
            const findDataset = () => {
              console.log('Getting all datasets for current user...');
              const userId = this.authService.getCurrentUserId();
              if (!userId) {
                return throwError(() => new Error('User ID required'));
              }
              
              const headers = {
                'Authorization': `Bearer ${this.authService.getToken()}`
              };
              
              const params = new HttpParams().set('userId', userId);
              
              return this.http.get<Dataset[]>(`${this.apiUrl}/datasets`, { 
                params, 
                headers
              }).pipe(
                tap(datasets => console.log(`Found ${datasets.length} datasets for user`)),
                map(datasets => {
                  // Try to find the dataset with our file ID
                  const dataset = datasets.find(ds => ds.file && ds.file.id === fileId);
                  if (!dataset) {
                    throw new Error(`No dataset found for file ID: ${fileId}`);
                  }
                  console.log('Found dataset:', dataset);
                  return dataset;
                })
              );
            };
            
            // Use retryWhen with a maximum number of attempts and exponential backoff
            return findDataset().pipe(
              retryWhen(errors => {
                let retries = 0;
                const maxRetries = 10;
                
                return errors.pipe(
                  delay(2000), // Wait 2 seconds before first retry
                  tap(() => {
                    retries++;
                    console.log(`Retry attempt ${retries}/${maxRetries} to find dataset...`);
                  }),
                  map(error => {
                    if (retries >= maxRetries) {
                      console.error('Maximum retries reached for finding dataset');
                      throw error;
                    }
                    return error;
                  }),
                  // Use a constant delay instead of dynamic calculation
                  delay(2000) // Fixed 2 second delay between retries
                );
              }),
              // Step 3: Once dataset is found, get its rows
              switchMap(dataset => {
                const datasetId = dataset.id;
                console.log(`Getting rows for dataset ID: ${datasetId}`);
                
                // Use the direct rows endpoint
                return this.getDatasetRows(datasetId, 0, 10000).pipe(
                  tap(rowsData => console.log(`Retrieved ${rowsData.content?.length || 0} rows`)),
                  map(rowsData => {
                    // Extract headers from the first row's data if available
                    const headers = rowsData.content && rowsData.content.length > 0 
                      ? Object.keys(rowsData.content[0].data || {})
                      : [];
                    
                    // Transform the dataset rows into a simpler format for the frontend
                    const data = rowsData.content?.map(row => row.data || {}) || [];
            
            return {
                      datasetId: datasetId,
              headers,
              data,
              totalRows: rowsData.totalElements,
                      currentPage: rowsData.currentPage,
                      totalPages: rowsData.totalPages || 1
            };
                  })
                );
              })
            );
          })
        );
      }),
      // Add timeout to prevent hanging indefinitely
      timeout(120000), // 2 minute timeout for the entire operation
      catchError(error => {
        console.error('Error in getDataPreview:', error);
        if (error.name === 'TimeoutError') {
          throw new Error('Operation timed out. The server may still be processing your file.');
        }
        throw error;
      })
    );
  }
  
  // Transformation methods
  applyTransformation(fileId: string, transformations: any[]): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/data/transform/${fileId}`, { transformations }).pipe(
      tap(result => console.log('Transformation applied', result)),
      catchError(error => {
        console.error('Error applying transformation', error);
        throw error;
      })
    );
  }
  
  /**
   * Change column data types for a dataset
   * @param datasetId Dataset identifier
   * @param changes List of column type changes to apply
   * @returns Observable of the transformation response
   */
  applyDataTypeChanges(datasetId: string, changes: ColumnTypeChange[]): Observable<TransformationResponse> {
    return this.http.post<TransformationResponse>(
      `${this.apiUrl}/transformations/data-types/${datasetId}`, 
      changes
    ).pipe(
      tap(response => console.log('Data type changes applied:', response)),
      catchError(error => {
        console.error('Error applying data type changes:', error);
        throw error;
      })
    );
  }
  
  /**
   * Get all available data types for column type selection
   */
  getAvailableDataTypes(): Observable<string[]> {
    // This could be an API call in the future, but for now returning static values
    return of(['STRING', 'INTEGER', 'FLOAT', 'DATE', 'BOOLEAN', 'DECIMAL']);
  }
  
  // Job methods
  getJobs(): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/jobs`).pipe(
      tap(jobs => console.log('Jobs retrieved', jobs)),
      catchError(error => {
        console.error('Error fetching jobs', error);
        return of([]);
      })
    );
  }
  
  getJobById(jobId: string): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/jobs/${jobId}`).pipe(
      tap(job => console.log('Job retrieved', job)),
      catchError(error => {
        console.error('Error fetching job', error);
        throw error;
      })
    );
  }
  
  cancelJob(jobId: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/jobs/${jobId}/cancel`, {}).pipe(
      tap(result => console.log('Job cancelled', result)),
      catchError(error => {
        console.error('Error cancelling job', error);
        throw error;
      })
    );
  }
  
  downloadJobResults(jobId: string): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/jobs/${jobId}/download`, {
      responseType: 'blob'
    }).pipe(
      tap(() => console.log('Job results downloaded')),
      catchError(error => {
        console.error('Error downloading job results', error);
        throw error;
      })
    );
  }
  
  // Upload a file (handles all sizes)
  smartUploadFile(file: File, description?: string): Observable<FileModel> {
    console.log(`Uploading file: ${file.name} (${file.size} bytes)`);
    
    // Create a subject to report progress
    const progressSubject = new Subject<number>();
    
    // Create an observable that will track the upload
    return new Observable<FileModel>(observer => {
      // Create form data for the upload
      const formData = new FormData();
      formData.append('file', file);
      if (description) {
        formData.append('description', description);
      }
      
      // Get authentication token
      const token = this.authService.getToken();
      if (!token) {
        observer.error(new Error('Authentication required'));
        return;
      }
      
      // Set up headers
      const headers = {
        'Authorization': `Bearer ${token}`
      };
      
      // Create the XMLHttpRequest to handle progress
      const xhr = new XMLHttpRequest();
      
      // Set longer timeout for large files (10 minutes)
      xhr.timeout = 600000;
      
      // Set up progress tracking
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) {
          const percentComplete = Math.round((event.loaded / event.total) * 100);
          progressSubject.next(percentComplete);
        }
      };
      
      // Set up completion handler
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          try {
            const response = JSON.parse(xhr.responseText);
            observer.next(response);
            observer.complete();
          } catch (error) {
            observer.error(new Error('Invalid response from server'));
          }
        } else {
          observer.error(new Error(`Server returned ${xhr.status}: ${xhr.statusText}`));
        }
      };
      
      // Set up error handler
      xhr.onerror = () => {
        observer.error(new Error('Network error occurred during upload'));
      };
      
      // Set up timeout handler
      xhr.ontimeout = () => {
        observer.error(new Error('Upload timeout - file may be too large or connection too slow'));
      };

            // Open the request
      xhr.open('POST', `/api/files/upload`, true);
      
      // Set the authorization header
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      
      // Send the form data
      xhr.send(formData);
      
      // Return cleanup function
      return () => {
        // Abort the request if the observer is unsubscribed
        if (xhr.readyState !== XMLHttpRequest.DONE) {
          xhr.abort();
        }
      };
    }).pipe(
      // Merge the progress events into the result stream
      tap(result => {
        if (typeof result === 'number') {
          console.log(`Upload progress: ${result}%`);
        } else {
          console.log('Upload complete:', result);
        }
      }),
      catchError(error => {
        console.error('Error in file upload:', error);
        throw error;
      })
    );
  }
  
  // Add this method to delete datasets
  deleteDataset(datasetId: string): Observable<any> {
    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      return throwError(() => new Error('Authentication required'));
    }
    
    const token = this.authService.getToken();
    if (!token) {
      return throwError(() => new Error('Authentication required'));
    }
    
    const headers = {
      'Authorization': `Bearer ${token}`
    };
    
    const params = new HttpParams().set('userId', userId);
    
    return this.http.delete<any>(
      `${this.apiUrl}/datasets/${datasetId}`, 
      { headers, params }
    ).pipe(
      tap(response => console.log('Dataset deleted successfully:', response)),
      catchError(error => {
        console.error('Error deleting dataset:', error);
        return throwError(() => error);
      })
    );
  }
  
  /**
   * Fix duplicate statistics for a dataset
   * Use this when encountering errors related to duplicate statistics
   * @param datasetId Dataset ID
   * @returns Observable with fix results
   */
  fixDatasetDuplicateStatistics(datasetId: string): Observable<any> {
    const url = `${this.apiUrl}/validation/datasets/${datasetId}/fix-duplicates`;
    const params = this.createUserParams();
    
    if (!params) {
      return throwError(() => new Error('Authentication required'));
    }
    
    return this.http.post<any>(url, null, { params }).pipe(
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
    console.error('API error:', errorMessage);
    return throwError(() => new Error(errorMessage));
  }

  /**
   * Export a dataset to CSV and trigger download
   * @param datasetId The ID of the dataset to export
   * @returns An Observable of a Blob containing the CSV data
   */
  exportDatasetToCsv(datasetId: string): Observable<Blob> {
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return throwError(() => new Error('Authentication required'));
    }

    // Add authorization header
    const headers = {
      'Authorization': `Bearer ${token}`,
      'Accept': 'text/csv'
    };

    // Send request to download endpoint
    return this.http.get(`${this.apiUrl}/export/csv/${datasetId}`, {
      headers,
      responseType: 'blob'
    }).pipe(
      tap(data => console.log('CSV export successful')),
      catchError(error => {
        console.error('Error exporting dataset to CSV', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Export a dataset to XLSX and trigger download
   * @param datasetId The ID of the dataset to export
   * @returns An Observable of a Blob containing the XLSX data
   */
  exportDatasetToXlsx(datasetId: string): Observable<Blob> {
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return throwError(() => new Error('Authentication required'));
    }

    // Add authorization header
    const headers = {
      'Authorization': `Bearer ${token}`,
      'Accept': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    };

    // Send request to download endpoint
    return this.http.get(`${this.apiUrl}/export/xlsx/${datasetId}`, {
      headers,
      responseType: 'blob'
    }).pipe(
      tap(data => console.log('XLSX export successful')),
      catchError(error => {
        console.error('Error exporting dataset to XLSX', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Export a dataset to XLS and trigger download
   * @param datasetId The ID of the dataset to export
   * @returns An Observable of a Blob containing the XLS data
   */
  exportDatasetToXls(datasetId: string): Observable<Blob> {
    const token = this.authService.getToken();
    if (!token) {
      console.error('Error: No authentication token available');
      return throwError(() => new Error('Authentication required'));
    }

    // Add authorization header
    const headers = {
      'Authorization': `Bearer ${token}`,
      'Accept': 'application/vnd.ms-excel'
    };

    // Send request to download endpoint
    return this.http.get(`${this.apiUrl}/export/xls/${datasetId}`, {
      headers,
      responseType: 'blob'
    }).pipe(
      tap(data => console.log('XLS export successful')),
      catchError(error => {
        console.error('Error exporting dataset to XLS', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Update dataset properties (name, description)
   * @param datasetId The ID of the dataset to update
   * @param updates Object containing properties to update
   * @returns Observable of the updated Dataset
   */
  updateDataset(datasetId: string, updates: { name?: string, description?: string }): Observable<Dataset> {
    const url = `${this.apiUrl}/datasets/${datasetId}`;
    const params = this.createUserParams();
    
    if (!params) {
      return throwError(() => new Error('Authentication required'));
    }
    
    const token = this.authService.getToken();
    if (!token) {
      return throwError(() => new Error('Authentication required'));
    }
    
    const headers = {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    };
    
    return this.http.put<Dataset>(url, updates, { params, headers }).pipe(
      catchError(error => {
        console.error('API error:', error);
        return throwError(() => error);
      })
    );
  }

  updateDatasetRows(datasetId: string, updatedRows: any[]): Observable<any> {
    const token = this.authService.getToken();
    if (!token) {
      return throwError(() => new Error('Authentication required'));
    }

    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      return throwError(() => new Error('User ID required'));
    }

    const headers = {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    };

    const params = new HttpParams().set('userId', userId);

    return this.http.put(
      `${this.apiUrl}/datasets/${datasetId}/rows`,
      updatedRows,
      { params, headers, responseType: 'text' }
    ).pipe(
      tap(() => console.log('Dataset rows updated successfully')
      

    
    ),
      catchError(error => {
        console.error('Erreur lors de la mise à jour des rows :', error);
        return throwError(() => error);
      })
    );
  }


} 