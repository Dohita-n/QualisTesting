import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DataService } from './data.service';
import { Dataset, DatasetColumn, DatasetRow } from '../models/dataset.model';

@Injectable({
  providedIn: 'root'
})
export class DatasetService {
  constructor(private dataService: DataService) {}
  
  /**
   * Get all datasets
   */
  getAllDatasets(): Observable<Dataset[]> {
    return this.dataService.getAllDatasets();
  }
  
  /**
   * Get a dataset by ID
   */
  getDatasetById(datasetId: string): Observable<Dataset> {
    return this.dataService.getDatasetById(datasetId);
  }
  
  /**
   * Get all columns for a dataset
   */
  getDatasetColumns(datasetId: string): Observable<DatasetColumn[]> {
    return this.dataService.getDatasetColumns(datasetId);
  }
  
  /**
   * Get rows for a dataset with pagination
   */
  getDatasetRows(datasetId: string, page: number = 0, size: number = 20): Observable<{ 
    content: DatasetRow[], 
    totalPages: number, 
    totalElements: number, 
    currentPage: number 
  }> {
    return this.dataService.getDatasetRows(datasetId, page, size);
  }
  
  /**
   * Delete a dataset
   */
  deleteDataset(datasetId: string): Observable<any> {
    return this.dataService.deleteDataset(datasetId);
  }
} 