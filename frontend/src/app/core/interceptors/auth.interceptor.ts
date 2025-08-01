import { Injectable } from '@angular/core';
import {
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpInterceptor,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { catchError, filter, take, switchMap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { AuthApiService } from '../../features/auth/service/auth-api/auth-api.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  // private isRefreshing = false;
  // private refreshTokenSubject: BehaviorSubject<any> = new BehaviorSubject<any>(null);

  constructor(
    private authService: AuthService,
    private authApiService: AuthApiService,
    private router: Router
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    // Let JwtInterceptor handle adding the token based on JwtModule config
    // The primary role of this interceptor can be token refreshing
    
    // Example of how to integrate refresh logic:
    /*
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && !this.isAuthRequest(request)) {
          return this.handle401Error(request, next);
        }
        return throwError(() => error);
      })
    );
    */
    
    // For now, just pass the request through
    return next.handle(request);
  }

  /*
  private addToken(request: HttpRequest<any>, token: string): HttpRequest<any> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  private handle401Error(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshTokenSubject.next(null);

      const refreshToken = this.authService.getRefreshToken();

      if (refreshToken) {
        return this.authApiService.refreshToken(refreshToken).pipe(
          switchMap(response => {
            this.isRefreshing = false;
            this.refreshTokenSubject.next(response.accessToken);
            // Use addToken here before retrying the request
            return next.handle(this.addToken(request, response.accessToken));
          }),
          catchError(error => {
            this.isRefreshing = false;
            this.authService.clearAuth();
            this.router.navigate(['/login']);
            return throwError(() => error);
          })
        );
      }
    }

    // If refreshing, queue the request until the new token is available
    return this.refreshTokenSubject.pipe(
      filter(token => token !== null),
      take(1),
      switchMap(token => next.handle(this.addToken(request, token)))
    );
  }

  private isAuthRequest(request: HttpRequest<any>): boolean {
    // Define requests that should not trigger refresh logic (e.g., login, register, refresh itself)
    return (
      request.url.includes('/auth/login') ||
      request.url.includes('/auth/register') ||
      request.url.includes('/auth/refresh')
    );
  }
  */
} 