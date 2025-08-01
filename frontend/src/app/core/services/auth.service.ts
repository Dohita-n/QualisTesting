import { Injectable, Inject, Optional } from '@angular/core';
import { JwtHelperService } from '@auth0/angular-jwt';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environements/environment';
import { Observable, of, BehaviorSubject } from 'rxjs';
import { catchError, tap, map, switchMap, shareReplay } from 'rxjs/operators';
import { User, UserProfile } from '../models/user.model';

// Interface for the API response
interface UserProfileResponse {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  avatar?: string;
  profiles: Array<{
    id: string;
    name: string;
    description?: string;
    roleNames?: string[];
  }>;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
  private readonly USER_DATA_KEY = 'user_data';
  private jwtHelper: JwtHelperService;
  
  // BehaviorSubject to track the current user
  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();
  
  // Cache for user profile data
  private userProfileCache: Observable<UserProfileResponse | null> | null = null;

  constructor(
    @Optional() jwtHelperService: JwtHelperService,
    private http: HttpClient
  ) {
    this.jwtHelper = jwtHelperService || new JwtHelperService();
    
    // Initialize user from localStorage on service creation
    const userData = this.getUser();
    if (userData) {
      this.currentUserSubject.next(userData);
    }
  }
  
  /**
   * Initialize the auth service - call this on app startup
   */
  initializeAuth(): Observable<User | null> {
    if (this.isAuthenticated()) {
      return this.fetchCurrentUserProfile().pipe(
        map(() => this.getUser()),
        catchError(() => of(null))
      );
    }
    return of(null);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpired(token);
  }

  hasRole(requiredRole: string): boolean {
    const user = this.getUser();
    if (!user) {
      console.log('No user found');
      return false;
    }
    
    // Direct role check (legacy)
    if (user.role === requiredRole) {
      console.log('Found role in user.role:', user.role);
      return true;
    }
    
    // Check in profiles (using the roleNames array inside each profile)
    if (user.profiles && user.profiles.length > 0) {
      
      
      // Check if any profile contains the required role in its roleNames array
      const hasRole = user.profiles.some(profile => {
        // Check if the profile has a roleNames property and it includes the required role
        const profileWithRoles = profile as any;
        if (profileWithRoles.roleNames && Array.isArray(profileWithRoles.roleNames)) {
          const roleFound = profileWithRoles.roleNames.includes(requiredRole);
          return roleFound;
        }
        // Fallback to direct name check if roleNames doesn't exist
        return profile.name === requiredRole;
      });
    
      return hasRole;
    }
    
    console.log('No profiles found');
    return false;
  }

  getUserRole(): string | null {
    return this.getUser()?.role || null;
  }

  getCurrentUser(): User | null {
    return this.getUser();
  }

  getCurrentUserId(): string | null {
    return this.getUser()?.id || null;
  }

  setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  setRefreshToken(token: string): void {
    localStorage.setItem(this.REFRESH_TOKEN_KEY, token);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  setUserData(user: User): void {
    localStorage.setItem(this.USER_DATA_KEY, JSON.stringify(user));
    this.currentUserSubject.next(user);
  }

  clearAuth(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    localStorage.removeItem(this.USER_DATA_KEY);
    this.currentUserSubject.next(null);
    this.userProfileCache = null;
  }

  /**
   * Get current user with detailed profile information from API
   * This method uses caching to prevent repeated API calls
   */
  fetchCurrentUserProfile(): Observable<UserProfileResponse | null> {
    // Clear cache if called explicitly to force refresh
    this.userProfileCache = null;
    
    if (!this.userProfileCache) {
      console.log('Fetching user profile from API: ' + `${environment.apiUrl}/user/me`);
      
      // Get the token directly
      const token = this.getToken();
      console.log('Using token:', token ? token.substring(0, 15) + '...' : 'NO TOKEN');
      
      // Create headers object properly typed
      const headers: Record<string, string> = {};
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
        headers['Content-Type'] = 'application/json';
      }
      
      this.userProfileCache = this.http.get<UserProfileResponse>(`${environment.apiUrl}/user/me`, { headers })
        .pipe(
          tap((userData: UserProfileResponse) => {
            console.log('User profile data received:', userData);
            // Update the user data in localStorage with the new profile information
            const existingUser = this.getUser();
            if (existingUser) {
              const updatedUser = {
                ...existingUser,
                id: userData.id,
                username: userData.username,
                email: userData.email,
                firstName: userData.firstName,
                lastName: userData.lastName,
                profiles: userData.profiles
              };
              console.log('Updating user data with:', updatedUser);
              this.setUserData(updatedUser);
              console.log('User data updated with profiles');
            } else {
              // Create new user object if none exists
              const newUser: User = {
                id: userData.id,
                username: userData.username,
                email: userData.email,
                firstName: userData.firstName,
                lastName: userData.lastName,
                profiles: userData.profiles
              };
              this.setUserData(newUser);
            }
          }),
          catchError(error => {
            console.error('Error fetching user profile', error);
            this.userProfileCache = null; // Clear cache on error
            return of(null);
          }),
          shareReplay(1) // Cache the last result
        );
    }
    
    return this.userProfileCache;
  }

  /**
   * Force refresh the user data from the API
   * Call this method when you need to ensure user data is up-to-date
   */
  refreshUserData(): Observable<UserProfileResponse | null> {
    this.userProfileCache = null; // Clear cache to force refresh
    return this.fetchCurrentUserProfile();
  }

  /**
   * Login method that fetches user profile data after successful authentication
   * @param credentials User credentials
   */
  login(credentials: { username: string, password: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiUrl}/auth/login`, credentials)
      .pipe(
        tap(response => {
          if (response.token) {
            this.setToken(response.token);
            if (response.refreshToken) {
              this.setRefreshToken(response.refreshToken);
            }
            
            // Initialize user data with basic info
            const userData: User = {
              id: response.userId || '',
              username: credentials.username,
              email: response.email || '',
              role: response.role || ''
            };
            
            this.setUserData(userData);
          }
        }),
        // Immediately fetch full profile after login
        switchMap(response => {
          if (response.token) {
            return this.fetchCurrentUserProfile().pipe(
              map(() => response)
            );
          }
          return of(response);
        })
      );
  }

  private getUser(): User | null {
    const userData = localStorage.getItem(this.USER_DATA_KEY);
    return userData ? JSON.parse(userData) : null;
  }

  private isTokenExpired(token: string): boolean {
    return this.jwtHelper?.isTokenExpired(token) ?? false;
  }
} 