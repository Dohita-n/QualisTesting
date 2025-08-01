import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environements/environment';
import { AuthService } from './auth.service';
import { User, UserProfile } from '../models/user.model';
import { map, catchError, switchMap, tap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private apiUrl = environment.apiUrl;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) { }

  /**
   * Get the current user's information directly from the API
   */
  getCurrentUser(): Observable<any> {
    return this.http.get(`${this.apiUrl}/user/me`, { headers: this.getAuthHeaders() });
  }
  
  /**
   * Get current user from the auth service (synchronous)
   */
  getUser(): User | null {
    return this.authService.getCurrentUser();
  }
  
  /**
   * Get current user profiles from the auth service
   * This first checks if profiles exist in the current user object
   * If not, it attempts to refresh the data from the API
   */
  getUserProfiles(): Observable<UserProfile[]> {
    const user = this.authService.getCurrentUser();
    
    // Check if we already have profiles
    if (user?.profiles && user.profiles.length > 0) {
      console.log('Returning cached profiles:', user.profiles);
      return of(user.profiles);
    }
    
    // Otherwise, force a refresh from the API
    console.log('No profiles found, fetching from API');
    return this.authService.refreshUserData().pipe(
      map(response => {
        const updatedUser = this.authService.getCurrentUser();
        return updatedUser?.profiles || [];
      }),
      tap(profiles => console.log('Fetched profiles:', profiles))
    );
  }
  
  /**
   * Explicitly refresh user profile data from the server
   */
  refreshUserData(): Observable<User | null> {
    return this.authService.refreshUserData().pipe(
      map(() => this.authService.getCurrentUser())
    );
  }

  /**
   * Update the current user's profile
   */
  updateProfile(profileData: { firstName?: string, lastName?: string, username?: string }): Observable<any> {
    return this.http.put(`${this.apiUrl}/user/profile`, profileData, { headers: this.getAuthHeaders() })
      .pipe(
        // Refresh user data after update
        switchMap(response => this.refreshUserData().pipe(map(() => response)))
      );
  }

  /**
   * Upload a new avatar image for the current user
   */
  uploadAvatar(formData: FormData): Observable<any> {
    const token = this.authService.getToken();
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
    return this.http.post(`${this.apiUrl}/user/avatar`, formData, { headers })
      .pipe(
        // Refresh user data after upload
        switchMap(response => this.refreshUserData().pipe(map(() => response)))
      );
  }

  /**
   * Change the current user's password
   */
  changePassword(passwordData: { currentPassword: string, newPassword: string }): Observable<any> {
    return this.http.put(`${this.apiUrl}/user/password`, passwordData, { headers: this.getAuthHeaders() });
  }


  /**
   * Check if the current user has a specific profile/role
   */
  hasProfile(profileName: string): Observable<boolean> {
    return this.getUserProfiles().pipe(
      map(profiles => {
        const hasProfile = profiles.some(profile => profile.name === profileName);
        console.log(`Checking profile ${profileName}:`, hasProfile);
        return hasProfile;
      })
    );
  }

  /**
   * Helper method to get auth headers
   */
  private getAuthHeaders(): HttpHeaders {
    const token = this.authService.getToken();
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    });
  }
} 