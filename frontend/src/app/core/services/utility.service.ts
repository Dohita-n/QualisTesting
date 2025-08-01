import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class UtilityService {
  
  constructor() {}
  
  /**
   * Format date to a user-friendly string
   * @param dateInput - Date string or Date object
   * @returns Formatted date string
   */
  formatDate(dateInput: string | Date): string {
    if (!dateInput) return 'N/A';
    
    const date = dateInput instanceof Date ? dateInput : new Date(dateInput);
    return date.toLocaleDateString('en-US', { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
  
  /**
   * Format file size to human-readable format
   * @param bytes - Size in bytes
   * @returns Formatted size string (e.g. '2.5 MB')
   */
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
} 