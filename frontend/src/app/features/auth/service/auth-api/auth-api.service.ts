import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { environment } from '../../../../../environements/environment';
import { AuthService } from '../../../../core/services/auth.service';

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  userId: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthApiService {
  private apiUrl = `${environment.apiUrl}/auth`;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, credentials, { withCredentials: true })
      .pipe(
        tap(response => {
          // Store the token and user data
          this.authService.setToken(response.accessToken);
          this.authService.setRefreshToken(response.refreshToken);
          this.authService.setUserData({
            id: response.userId,
            username: response.username,
            email: response.email,
            firstName: response.firstName,
            lastName: response.lastName,
            role: response.role
          });
        }),
        catchError(this.handleError)
      );
  }

  register(userData: RegisterRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, userData)
      .pipe(
        catchError(this.handleError)
      );
  }

  refreshToken(refreshToken: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/refresh`, { refreshToken }, { withCredentials: true })
      .pipe(
        tap(response => {
          // Update the access token
          this.authService.setToken(response.accessToken);
        }),
        catchError(this.handleError)
      );
  }

  logout(): Observable<any> {
    const refreshToken = this.authService.getRefreshToken();
    // Clear local storage regardless of API call success
    this.authService.clearAuth();
    
    return this.http.post(`${this.apiUrl}/logout`, { refreshToken }, { withCredentials: true })
      .pipe(
        catchError(this.handleError)
      );
  }

  private handleError(error: HttpErrorResponse) {
    let errorMessage = 'An unknown error occurred';
    
    if (error.error instanceof ErrorEvent) {
      // Client-side error
      errorMessage = `Error: ${error.error.message}`;
    } else {
      // Server-side error
      if (error.error && error.error.message) {
        errorMessage = error.error.message;
      } else if (error.status) {
        switch (error.status) {
          case 401:
            errorMessage = 'Unauthorized: Please login again';
            break;
          case 403:
            errorMessage = 'Access denied: You do not have permission';
            break;
          case 404:
            errorMessage = 'Resource not found';
            break;
          case 500:
            errorMessage = 'Server error: Please try again later';
            break;
          default:
            errorMessage = `Error Code: ${error.status}, Message: ${error.message}`;
        }
      }
    }
    
    return throwError(() => new Error(errorMessage));
  }
} 