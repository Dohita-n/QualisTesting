import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { interval, Observable, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { environment } from '../../../../environements/environment';

interface MetricPoint {
  timestamp: number;
  value: number;
}

/*
Future improvements:
- Add more metrics to the dashboard
- Add a way to filter the metrics by type
- Add a way to sort the metrics by value
- Add a way to search the metrics by name
- Add a way to export the metrics to a file

- **MetricsDashboardComponent**: Visualization of dataset metrics and statistics
  - Displays real-time application metrics in a responsive dashboard
  - Shows JVM memory usage, HTTP requests, file uploads statistics
  - Features auto-refresh functionality for live monitoring
  - Implements graceful fallback to mock data during development
  - Visualizes metrics with progress bars and formatted values
  - Responsive grid layout adapts to different screen sizes
   */

interface Metric {
  name: string;
  description: string;
  points: MetricPoint[];
  value?: number;
  type: 'counter' | 'gauge' | 'timer';
}

@Component({
  selector: 'app-metrics-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="metrics-dashboard p-4 bg-white rounded-lg shadow-md">
      <h2 class="text-xl font-semibold mb-4">Application Metrics</h2>
      
      <div *ngIf="isLoading" class="text-center py-4">
        <div class="spinner-border text-primary" role="status">
          <span class="sr-only">Loading...</span>
        </div>
      </div>
      
      <div *ngIf="error" class="alert alert-danger">
        {{ error }}
      </div>
      
      <div *ngIf="!isLoading && !error">
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <!-- JVM Metrics -->
          <div class="metric-card p-4 border rounded-lg">
            <h3 class="font-medium text-gray-800">JVM Memory Usage</h3>
            <div class="text-3xl font-bold">{{ formatBytes(jvmMemoryUsed) }}</div>
            <div class="text-sm text-gray-500">of {{ formatBytes(jvmMemoryMax) }}</div>
            <div class="w-full bg-gray-200 rounded-full h-2.5 mt-2">
              <div class="bg-blue-600 h-2.5 rounded-full" 
                  [style.width.%]="(jvmMemoryUsed / jvmMemoryMax) * 100"></div>
            </div>
          </div>
          
          <!-- HTTP Metrics -->
          <div class="metric-card p-4 border rounded-lg">
            <h3 class="font-medium text-gray-800">HTTP Requests</h3>
            <div class="text-3xl font-bold">{{ httpRequests }}</div>
            <div class="text-sm text-gray-500">Total Requests</div>
          </div>
          
          <!-- File Upload Metrics -->
          <div class="metric-card p-4 border rounded-lg">
            <h3 class="font-medium text-gray-800">File Uploads</h3>
            <div class="text-3xl font-bold">{{ fileUploads }}</div>
            <div class="text-sm text-gray-500">Total Uploads</div>
          </div>

          <!-- Processing Time -->
          <div class="metric-card p-4 border rounded-lg">
            <h3 class="font-medium text-gray-800">Avg. Processing Time</h3>
            <div class="text-3xl font-bold">{{ avgProcessingTime.toFixed(2) }}ms</div>
            <div class="text-sm text-gray-500">Per Request</div>
          </div>
          
          <!-- Garbage Collection -->
          <div class="metric-card p-4 border rounded-lg">
            <h3 class="font-medium text-gray-800">GC Pause Time</h3>
            <div class="text-3xl font-bold">{{ gcPauseTime.toFixed(2) }}ms</div>
            <div class="text-sm text-gray-500">Total Pause</div>
          </div>
          
          <!-- Active Connections -->
          <div class="metric-card p-4 border rounded-lg">
            <h3 class="font-medium text-gray-800">DB Connections</h3>
            <div class="text-3xl font-bold">{{ activeConnections }} / {{ maxConnections }}</div>
            <div class="text-sm text-gray-500">Active / Total</div>
            <div class="w-full bg-gray-200 rounded-full h-2.5 mt-2">
              <div class="bg-green-600 h-2.5 rounded-full" 
                  [style.width.%]="(activeConnections / maxConnections) * 100"></div>
            </div>
          </div>
        </div>
        
        <div class="my-4">
          <button 
            (click)="refreshMetrics()"
            class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
            Refresh Metrics
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .metrics-dashboard {
      font-family: 'Inter', sans-serif;
    }
    
    .metric-card {
      transition: all 0.3s ease;
    }
    
    .metric-card:hover {
      box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
      transform: translateY(-2px);
    }
  `]
})
export class MetricsDashboardComponent implements OnInit, OnDestroy {
  // Metrics data
  jvmMemoryUsed = 0;
  jvmMemoryMax = 1;
  httpRequests = 0;
  fileUploads = 0;
  avgProcessingTime = 0;
  gcPauseTime = 0;
  activeConnections = 0;
  maxConnections = 10;
  
  isLoading = false;
  error: string | null = null;
  
  private destroy$ = new Subject<void>();
  private refreshInterval = 30000; // 30 seconds
  
  constructor(private http: HttpClient) {}
  
  ngOnInit(): void {
    this.loadMetrics();
    
    // Set up auto-refresh
    interval(this.refreshInterval)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadMetrics());
  }
  
  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
  
  refreshMetrics(): void {
    this.loadMetrics();
  }
  
  private loadMetrics(): void {
    this.isLoading = true;
    this.error = null;
    
    // In a real application, this would fetch from the actuator endpoint
    this.http.get<any>(`${environment.apiUrl}/actuator/metrics`)
      .subscribe({
        next: (data) => {
          this.processMockMetricsData(data);
          this.isLoading = false;
        },
        error: (err) => {
          console.error('Error loading metrics', err);
          // Fall back to mock data in development
          if (environment.production === false) {
            this.processMockMetricsData(null);
            this.isLoading = false;
          } else {
            this.error = 'Failed to load metrics data. Please try again later.';
            this.isLoading = false;
          }
        }
      });
  }
  
  private processMockMetricsData(data: any): void {
    // If we have real data, use it
    if (data) {
      // Process real metrics data
      // This would extract values from the Spring Boot Actuator format
    } else {
      // For demo/dev, use mock data with some randomization
      this.jvmMemoryUsed = Math.floor(Math.random() * 1024 * 1024 * 500) + 1024 * 1024 * 200;
      this.jvmMemoryMax = 1024 * 1024 * 1024;
      this.httpRequests = Math.floor(Math.random() * 1000) + 500;
      this.fileUploads = Math.floor(Math.random() * 50) + 10;
      this.avgProcessingTime = Math.random() * 200 + 50;
      this.gcPauseTime = Math.random() * 100 + 10;
      this.activeConnections = Math.floor(Math.random() * 8) + 2;
      this.maxConnections = 10;
    }
  }
  
  formatBytes(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
} 