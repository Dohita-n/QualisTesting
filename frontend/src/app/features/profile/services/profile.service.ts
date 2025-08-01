import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../../environements/environment';
import { Profile, Role } from '../models/profile.model';

@Injectable({
  providedIn: 'root'
})
export class ProfileService {
  private apiUrl = `${environment.apiUrl}/admin`;

  constructor(private http: HttpClient) {}

  // Profile CRUD operations
  getAllProfiles(): Observable<Profile[]> {
    return this.http.get<any[]>(`${this.apiUrl}/profiles`).pipe(
      map(profiles => profiles.map(profile => this.mapProfileFromApi(profile)))
    );
  }

  getProfileById(id: string): Observable<Profile> {
    return this.http.get<any>(`${this.apiUrl}/profiles/${id}`).pipe(
      map(profile => this.mapProfileFromApi(profile))
    );
  }

  createProfile(profile: Profile): Observable<Profile> {
    const profileDTO = this.mapProfileToApi(profile);
    return this.http.post<any>(`${this.apiUrl}/profiles`, profileDTO).pipe(
      map(profile => this.mapProfileFromApi(profile))
    );
  }

  updateProfile(id: string, profile: Profile): Observable<Profile> {
    const profileDTO = this.mapProfileToApi(profile);
    return this.http.put<any>(`${this.apiUrl}/profiles/${id}`, profileDTO).pipe(
      map(profile => this.mapProfileFromApi(profile))
    );
  }

  deleteProfile(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/profiles/${id}`);
  }

  // Role operations
  getAllRoles(): Observable<Role[]> {
    return this.http.get<any[]>(`${this.apiUrl}/roles`).pipe(
      map(roles => roles.map(role => ({
        id: role.id,
        name: role.name,
        description: role.description
      })))
    );
  }

  // Profile-Role assignment operations
  assignRolesToProfile(profileId: string, roleIds: string[]): Observable<Profile> {
    return this.http.post<any>(`${this.apiUrl}/profiles/${profileId}/roles`, roleIds).pipe(
      map(profile => this.mapProfileFromApi(profile))
    );
  }

  removeRolesFromProfile(profileId: string, roleIds: string[]): Observable<Profile> {
    return this.http.delete<any>(`${this.apiUrl}/profiles/${profileId}/roles`, { body: roleIds }).pipe(
      map(profile => this.mapProfileFromApi(profile))
    );
  }

  // Helper methods to map between API and model formats
  private mapProfileFromApi(apiProfile: any): Profile {
    console.log("API Profile:", apiProfile);
    
    // Get role objects if available
    const roleObjs = apiProfile.roles || [];
    const roleNames = apiProfile.roleNames || [];
    
    // Extract role IDs from role objects if they exist
    let roleIds: string[] = [];
    if (roleObjs.length > 0 && roleObjs[0].id) {
      roleIds = roleObjs.map((role: any) => role.id);
    } else if (apiProfile.roleIds) {
      roleIds = apiProfile.roleIds;
    }
    
    const profile = {
      id: apiProfile.id,
      name: apiProfile.name,
      description: apiProfile.description,
      roles: roleNames,
      roleIds: roleIds
    };
    
    console.log("Mapped Profile:", profile);
    return profile;
  }

  private mapProfileToApi(profile: Profile): any {
    return {
      id: profile.id,
      name: profile.name,
      description: profile.description,
      roleNames: profile.roles
    };
  }
} 