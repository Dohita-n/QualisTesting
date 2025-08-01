import { inject } from '@angular/core';
import { Router, CanActivateFn, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Checks if the user has one of the required roles/authorities
 * @param requiredRoles Array of roles/authorities that can access the route
 */
export function hasAnyRole(authService: AuthService, requiredRoles: string[]): boolean {
  console.log('hasAnyRole check for:', requiredRoles);
  
  if (!requiredRoles || requiredRoles.length === 0) {
    console.log('No roles required, allowing access');
    return true;
  }
  
  // Debug current user data
  const user = authService.getCurrentUser();
  console.log('Current user:', user?.username, 'Profiles:', user?.profiles?.map(p => p.name).join(', '));
  
  for (const role of requiredRoles) {
    console.log(`Checking if user has role: ${role}`);
    if (authService.hasRole(role)) {
      console.log(`User has required role: ${role}`);
      return true;
    }
  }
  
  console.log('User does not have any of the required roles:', requiredRoles);
  return false;
}

/**
 * Guard factory that creates a guard requiring one of the specified roles
 * @param requiredRoles Array of roles that can access the route
 */
export const roleGuard = (requiredRoles: string[]): CanActivateFn => {
  return (route, state): boolean | UrlTree => {
    const authService = inject(AuthService);
    const router = inject(Router);
    
    console.log(`Role guard activated for route: ${state.url}`);
    console.log(`Required roles: ${requiredRoles.join(', ')}`);
    
    if (!authService.isAuthenticated()) {
      console.log('User is not authenticated, redirecting to login');
      return router.createUrlTree(['/login'], {
        queryParams: { returnUrl: state.url }
      });
    }
    
    if (!hasAnyRole(authService, requiredRoles)) {
      console.log('Access denied, redirecting to unauthorized page');
      return router.createUrlTree(['/unauthorized'], {
        queryParams: { 
          requiredPermission: requiredRoles.join(' or '),
          returnUrl: state.url
        }
      });
    }
    
    console.log('Access granted');
    return true;
  };
};

// Predefined role guards based on backend security configuration
export const viewDataGuard = roleGuard(['VIEW_DATA', 'ADMIN']);
export const editDataGuard = roleGuard(['EDIT_DATA', 'ADMIN']);
export const uploadDataGuard = roleGuard(['UPLOAD_DATA', 'ADMIN']);
export const exportDataGuard = roleGuard(['EXPORT_DATA', 'ADMIN']);
export const deleteDataGuard = roleGuard(['DELETE_DATA', 'ADMIN']);
export const adminGuard = roleGuard(['ADMIN']); 