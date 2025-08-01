import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { ConfirmationService, MessageService } from 'primeng/api';
import { InputTextModule } from 'primeng/inputtext';
import { TooltipModule } from 'primeng/tooltip';
import { TagModule } from 'primeng/tag';
import { MultiSelectModule } from 'primeng/multiselect';
import { Table } from 'primeng/table';
import { AuthService } from '../../../../core/services/auth.service';
import { environment } from '../../../../../environements/environment';
import { ProfileService } from '../../services/profile.service';
import { Profile, Role } from '../../models/profile.model';
import { finalize } from 'rxjs/operators';
import { forkJoin, Observable } from 'rxjs';

@Component({
  selector: 'app-profiles-assignments',
  standalone: true,
  imports: [
    CommonModule,
    MultiSelectModule,
    TagModule,
    FormsModule,
    TableModule,
    DialogModule,
    CheckboxModule,
    ButtonModule,
    ConfirmDialogModule,
    ToastModule,
    InputTextModule,
    TooltipModule,
  ],
  templateUrl: './profiles-assignments.component.html',
  styleUrls: ['./profiles-assignments.component.css'],
  providers: [ConfirmationService, MessageService]
})
export class ProfilesAssignmentsComponent implements OnInit {
  searchValue: string = '';
  profiles: Profile[] = [];
  availableRoles: { label: string, value: string }[] = [];
  roleMap: Map<string, string> = new Map(); // Maps role name to ID
  loading: boolean = true;
  showProfileDialog: boolean = false;
  isEditMode: boolean = false;
  originalProfile: Profile | null = null;
  newProfile: Profile = { name: '', description: '', roles: [], roleIds: [] };
  formChanged: boolean = false;
  selectedProfileId: string | null = null;

  @ViewChild('dt') table!: Table;

  constructor(
    private confirmationService: ConfirmationService,
    private messageService: MessageService,
    private profileService: ProfileService
  ) {}

  ngOnInit() {
    // Load roles first, then profiles (so we have the role mapping available)
    this.loadRoles();
  }

  loadProfiles() {
    this.loading = true;
    this.profileService.getAllProfiles()
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: (data) => {
          console.log("Loaded profiles:", data);
          // If roleIds are missing, we need to derive them from the roleMap
          this.profiles = data.map(profile => {
            if (!profile.roleIds || profile.roleIds.length === 0) {
              profile.roleIds = profile.roles.map(roleName => {
                // Look up the ID for this role name
                return this.roleMap.get(roleName) || '';
              }).filter(id => id); // Remove empty IDs
            }
            return profile;
          });
        },
        error: (error) => {
          console.error('Error loading profiles', error);
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to load profiles'
          });
        }
      });
  }

  loadRoles() {
    this.loading = true;
    this.profileService.getAllRoles().subscribe({
      next: (roles) => {
        // Create a map of role name to ID for later use
        this.roleMap.clear();
        roles.forEach(role => {
          if (role.id && role.name) {
            this.roleMap.set(role.name, role.id);
          }
        });
        
        this.availableRoles = roles.map(role => ({
          label: role.name,
          value: role.id || '' // Use ID as value
        }));
        
        // Now that roles are loaded, load profiles
        this.loadProfiles();
      },
      error: (error) => {
        console.error('Error loading roles', error);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load roles'
        });
        this.loading = false;
      }
    });
  }

  openProfileDialog() {
    this.isEditMode = false;
    this.newProfile = { name: '', description: '', roles: [], roleIds: [] };
    this.originalProfile = null;
    this.formChanged = false;
    this.showProfileDialog = true;
  }

  editProfile(profile: Profile) {
    this.isEditMode = true;
    this.selectedProfileId = profile.id!;
    this.originalProfile = {...profile};
    
    // Initialize the form with current profile data
    this.newProfile = {
      name: profile.name,
      description: profile.description || '',
      roles: [...profile.roles],
      roleIds: [...(profile.roleIds || [])]
    };
    
    console.log("Editing profile with roles:", this.newProfile.roles);
    console.log("Role IDs:", this.newProfile.roleIds);
    
    this.formChanged = false;
    this.showProfileDialog = true;
    this.messageService.add({
      severity: 'info',
      summary: 'Modification',
      detail: 'Editing profile'
    });
  }

  onProfileFormChange() {
    if (!this.originalProfile) {
      this.formChanged = true;
      return;
    }
    
    // Check if anything has changed
    const rolesEqual = 
      this.newProfile.roleIds?.length === this.originalProfile.roleIds?.length && 
      this.newProfile.roleIds?.every(roleId => this.originalProfile?.roleIds?.includes(roleId) ?? false);
      
    this.formChanged = 
      this.newProfile.name !== this.originalProfile?.name ||
      this.newProfile.description !== this.originalProfile?.description ||
      !rolesEqual;
  }

  // When roles are selected in the dropdown
  onRolesSelected(event: any) {
    // event.value contains the selected role IDs
    this.newProfile.roleIds = [...event.value];
    
    // Update role names based on selected IDs
    this.newProfile.roles = this.newProfile.roleIds.map(id => {
      // Find the role name from available roles
      const role = this.availableRoles.find(r => r.value === id);
      return role ? role.label : '';
    }).filter(name => name); // Remove empty names
    
    this.onProfileFormChange();
  }

  confirm() {
    if (!this.newProfile.name) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Profile name is required'
      });
      return;
    }

    if (this.isEditMode && this.selectedProfileId) {
      this.updateProfile();
    } else {
      this.createProfile();
    }
  }

  createProfile() {
    this.loading = true;
    
    // Step 1: Create the profile
    this.profileService.createProfile(this.newProfile)
      .subscribe({
        next: (createdProfile) => {
          // Step 2: If we have role IDs, assign them to the profile
          if (this.newProfile.roleIds && this.newProfile.roleIds.length > 0 && createdProfile.id) {
            this.profileService.assignRolesToProfile(createdProfile.id, this.newProfile.roleIds)
              .subscribe({
                next: () => {
                  this.finalizeProfileOperation('created');
                },
                error: (error) => {
                  console.error('Error assigning roles to profile', error);
                  // Still consider the operation successful, just show a warning
                  this.messageService.add({
                    severity: 'warn',
                    summary: 'Warning',
                    detail: 'Profile created but roles could not be assigned'
                  });
                  this.finalizeProfileOperation('created');
                }
              });
          } else {
            this.finalizeProfileOperation('created');
          }
        },
        error: (error) => {
          console.error('Error creating profile', error);
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to create profile'
          });
          this.loading = false;
        }
      });
  }

  updateProfile() {
    if (!this.selectedProfileId) return;

    this.loading = true;
    
    // Step 1: Update basic profile information
    this.profileService.updateProfile(this.selectedProfileId, this.newProfile)
      .subscribe({
        next: (updatedProfile) => {
          // Step 2: Handle role changes if necessary
          if (this.originalProfile?.roleIds && this.newProfile.roleIds && updatedProfile.id) {
            // Find role IDs to add and remove
            const toAdd = this.newProfile.roleIds.filter(id => 
              !this.originalProfile?.roleIds?.includes(id));
            const toRemove = this.originalProfile.roleIds.filter(id => 
              !this.newProfile.roleIds?.includes(id));
            
            // Create an array of observables for operations that need to be performed
            const operations: Observable<any>[] = [];
            
            // Add new roles if needed
            if (toAdd.length > 0) {
              operations.push(
                this.profileService.assignRolesToProfile(updatedProfile.id, toAdd)
              );
            }
            
            // Remove roles if needed
            if (toRemove.length > 0) {
              operations.push(
                this.profileService.removeRolesFromProfile(updatedProfile.id, toRemove)
              );
            }
            
            // Execute all operations and finalize when done
            if (operations.length > 0) {
              forkJoin(operations).subscribe({
                next: () => {
                  this.finalizeProfileOperation('updated');
                },
                error: (error) => {
                  console.error('Error updating profile roles', error);
                  this.messageService.add({
                    severity: 'warn',
                    summary: 'Warning',
                    detail: 'Profile updated but role changes could not be applied'
                  });
                  this.finalizeProfileOperation('updated');
                }
              });
            } else {
              this.finalizeProfileOperation('updated');
            }
          } else {
            this.finalizeProfileOperation('updated');
          }
        },
        error: (error) => {
          console.error('Error updating profile', error);
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to update profile'
          });
          this.loading = false;
        }
      });
  }

  finalizeProfileOperation(operation: 'created' | 'updated') {
    this.loading = false;
    this.loadProfiles();
    this.showProfileDialog = false;
    this.messageService.add({
      severity: 'success',
      summary: 'Success',
      detail: `Profile ${operation} successfully`
    });
  }

  
  /**
   * Get appropriate severity (color) for role tags
   */
  getRoleSeverity(roleName: string): string {
    if (!roleName) return 'info';
    
    // Normalize by trimming whitespace and converting to uppercase
    const normalizedRole = roleName.trim().toUpperCase();
    
    
    // Remove any extra spaces that might be in the role name
    const cleanRole = normalizedRole.replace(/\s+/g, '');
    
    switch (cleanRole) {
      case 'ADMIN': 
        return 'danger';  // Red
      case 'DELETE_DATA': 
        return 'danger';  // Red
      case 'EDIT_DATA': 
        return 'warning'; // Orange
      case 'UPLOAD_DATA': 
        return 'success'; // Green
      case 'EXPORT_DATA': 
        return 'info';    // Blue
      case 'VIEW_DATA': 
        return 'secondary'; // Gray/Purple
      default: 
        console.log('Unknown role in profiles assignment:', cleanRole);
        return 'info';
    }
  }


  resetForm() {
    this.newProfile = { name: '', description: '', roles: [], roleIds: [] };
    this.formChanged = false;
  }

  confirmDelete(profile: Profile) {
    if (profile.name.toLowerCase() === 'admin') {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Admin profile cannot be deleted'
      });
      return;
    }
    
    this.confirmationService.confirm({
      message: 'Are you sure you want to delete this profile?',
      header: 'Confirmation',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.deleteProfile(profile);
      },
      reject: () => {
        this.messageService.add({
          severity: 'info',
          summary: 'Cancelled',
          detail: 'Deletion cancelled'
        });
      }
    });
  }

  deleteProfile(profile: Profile) {
    if (!profile.id) return;
    
    this.loading = true;
    this.profileService.deleteProfile(profile.id)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: () => {
          this.loadProfiles();
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: 'Profile successfully deleted'
          });
        },
        error: (error) => {
          console.error('Error deleting profile', error);
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to delete profile'
          });
        }
      });
  }

  onFilter() {
    if (this.table) {
      this.table.filterGlobal(this.searchValue, 'contains');
    }
  }
}