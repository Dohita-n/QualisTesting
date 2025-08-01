import { HttpInterceptorFn } from '@angular/common/http';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('auth_token');
  console.log('JWT Interceptor called for:', req.url);
  console.log('Token exists:', !!token);
  
  if (token) {
    // Don't add token to login/register/refresh endpoints
    if (
      req.url.includes('/auth/login') || 
      req.url.includes('/auth/register') || 
      req.url.includes('/auth/refresh')
    ) {
      return next(req);
    }
    
    // Clone the request and add the Authorization header
    const clonedReq = req.clone({
      withCredentials: true,
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    console.log('Added Authorization header to request');
    console.log('Cloned Request with withCredentials:', clonedReq.withCredentials); 
    return next(clonedReq);
  }
  
  return next(req);
}; 