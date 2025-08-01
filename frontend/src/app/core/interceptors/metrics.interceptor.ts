import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { tap, finalize } from 'rxjs/operators';

// Simple metrics tracker
const apiMetrics: { [endpoint: string]: { totalCalls: number, successCalls: number, errorCalls: number, totalTime: number } } = {};

export const metricsInterceptor: HttpInterceptorFn = (req, next) => {
  const startTime = Date.now();
  const url = req.url;
  
  // Extract the API endpoint from the URL
  const endpoint = url.replace(/\?.*$/, ''); // Remove query params
  
  // Initialize metrics for this endpoint if not exists
  if (!apiMetrics[endpoint]) {
    apiMetrics[endpoint] = { totalCalls: 0, successCalls: 0, errorCalls: 0, totalTime: 0 };
  }
  
  // Increment total calls
  apiMetrics[endpoint].totalCalls++;
  
  return next(req).pipe(
    tap(event => {
      // Only track responses
      if (event instanceof HttpResponse) {
        // Increment successful calls
        apiMetrics[endpoint].successCalls++;
      }
    }),
    finalize(() => {
      // Calculate and store response time
      const duration = Date.now() - startTime;
      apiMetrics[endpoint].totalTime += duration;
      
      // Log metrics occasionally (e.g., every 10 calls)
      if (apiMetrics[endpoint].totalCalls % 10 === 0) {
        const metrics = apiMetrics[endpoint];
        console.log(`API Metrics for ${endpoint}:`, {
          totalCalls: metrics.totalCalls,
          successRate: `${(metrics.successCalls / metrics.totalCalls * 100).toFixed(1)}%`,
          avgResponseTime: `${(metrics.totalTime / metrics.totalCalls).toFixed(1)}ms`
        });
      }
    })
  );
}; 