import { HttpInterceptorFn } from '@angular/common/http';

/**
 * This interceptor ensures all requests use our proxy instead of directly accessing
 * the backend server, which helps avoid CORS and certificate validation issues.
 */
export const corsFixInterceptor: HttpInterceptorFn = (req, next) => {
  console.log('CORS Fix Interceptor handling request to:', req.url);
  
  // Only modify requests to our API
  if (req.url.includes('/api/')) {
    // Don't include the full URL since the proxy will handle it
    const apiPath = req.url.substring(req.url.indexOf('/api/'));
    
    console.log('Rewriting URL from', req.url, 'to', apiPath);
    
    // Create a new request with the simplified URL that will go through the proxy
    const proxyReq = req.clone({
      url: apiPath,
      withCredentials: true
    });
    
    return next(proxyReq);
  }
  
  return next(req);
}; 