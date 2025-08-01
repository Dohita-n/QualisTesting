import { inject } from '@angular/core';
import { Router, CanActivateFn, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { hasAnyRole } from './role.guard';

export const authGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  if (!authService.isAuthenticated()) {
    // Redirect to login page with the return url
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url }
    });
  }
  
  // Check if the route requires a specific role
  const requiredRole = route.data?.['role'];
  if (requiredRole && !authService.hasRole(requiredRole)) {
    // User is authenticated but doesn't have the required role
    return router.createUrlTree(['/unauthorized']);
  }
  
  return true;
};

// Deprecated: Use the roleGuard from role.guard.ts instead
export const roleGuard = (requiredRole: string): CanActivateFn => {
  return (route, state): boolean | UrlTree => {
    const authService = inject(AuthService);
    const router = inject(Router);
    
    if (!authService.isAuthenticated()) {
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url }
      });
    }
    
    
    if (!authService.hasRole(requiredRole)) {
      return router.createUrlTree(['/unauthorized']);
    }
    
    return true;
  };
};
