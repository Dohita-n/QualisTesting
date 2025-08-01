import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * @deprecated Use adminGuard from role.guard.ts instead
 */
export const adminGuard: CanActivateFn = (route, state): boolean | UrlTree => {
  console.warn('This adminGuard is deprecated. Please use the adminGuard from role.guard.ts');
  const authService = inject(AuthService);
  const router = inject(Router);
  
  if (!authService.isAuthenticated()) {
    // Redirect to login page with the return url
    return router.createUrlTree(['/login'], {
      queryParams: { returnUrl: state.url }
    });
  }
  
  if (!authService.hasRole('ADMIN')) {
    // User is authenticated but not an admin
    return router.createUrlTree(['/unauthorized']);
} 
  
  return true;
}; 