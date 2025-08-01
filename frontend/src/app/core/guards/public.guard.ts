import { inject } from '@angular/core';
import { Router, CanActivateFn, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const publicGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  // If the user is already authenticated, redirect to dashboard
  if (authService.isAuthenticated()) {
    // Check if there's a returnUrl in the current URL's query params
    const returnUrl = route.queryParams['returnUrl'] || '/dashboard';
    
    // Preserve all other query parameters during redirection
    const queryParams: Record<string, string> = {};
    for (const key in route.queryParams) {
      if (key !== 'returnUrl') {
        queryParams[key] = route.queryParams[key];
      }
    }
    
    // Create URL tree for the redirection, preserving query params
    return router.createUrlTree([returnUrl], {
      queryParams: Object.keys(queryParams).length > 0 ? queryParams : undefined
    });
  }
  
  // If user is not authenticated, allow access to the public route

  return true;
}; 