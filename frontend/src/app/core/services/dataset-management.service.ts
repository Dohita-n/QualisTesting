import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Dataset } from '../models/dataset.model';
import { DataService } from './data.service';

@Injectable({
  providedIn: 'root'
})
export class DatasetManagementService {
  private datasetsChangedSource = new Subject<void>();
  public datasetsChanged$ = this.datasetsChangedSource.asObservable();
  
  constructor(private dataService: DataService) {}
  
  /**
   * Load all datasets belonging to the current user
   * @returns Observable of datasets array
   */
  loadUserDatasets(): Observable<Dataset[]> {
    return this.dataService.getAllDatasets();
  }
  
  /**
   * Delete a dataset by ID
   * @param datasetId The ID of the dataset to delete
   * @returns Observable of the deletion result
   */
  deleteDataset(datasetId: string): Observable<any> {
    return this.dataService.deleteDataset(datasetId);
  }
  
  /**
   * Notify all subscribers that datasets have changed
   * This helps to refresh lists after operations
   */
  notifyDatasetsChanged(): void {
    this.datasetsChangedSource.next();
  }
} 