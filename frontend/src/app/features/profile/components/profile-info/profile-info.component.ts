import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { InputTextModule } from 'primeng/inputtext';
import { CardModule } from 'primeng/card';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { PasswordModule } from 'primeng/password';
import { FileUploadModule } from 'primeng/fileupload';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MultiSelectModule } from 'primeng/multiselect';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { UserService } from '../../../../core/services/user.service';

interface User {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  avatar?: string;
  profiles?: {
    id: string;
    name: string;
    description?: string;
    roleNames?: string[];
  }[];
}

interface PasswordData {
  current: string;
  new: string;
  confirm: string;
}

interface Role {
  name: string;
}

@Component({
  selector: 'app-profile-info',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    InputTextModule,
    CardModule,
    ButtonModule,
    AvatarModule,
    TagModule,
    DialogModule,
    PasswordModule,
    FileUploadModule,
    MultiSelectModule,
    ToastModule
  ],
  providers: [MessageService],
  templateUrl: './profile-info.component.html',
  styleUrls: ['./profile-info.component.css']
})
export class ProfileInfoComponent implements OnInit {
  @Output() profileUpdated = new EventEmitter<User>();
  @Output() passwordChanged = new EventEmitter<{ oldPassword: string; newPassword: string }>();

  isEditing = false;
  showPasswordDialog = false;
  isChangingPassword = false;
  isLoading = true;
  isAdmin = false;

  user: User = {
    username: '',
    email: '',
    firstName: '',
    lastName: '',
    avatar: '/default-avatar.png',
    profiles: []
  };

  editUser: User = { ...this.user };
  
  passwordData: PasswordData = {
    current: '',
    new: '',
    confirm: ''
  };

  availableRoles: Role[] = [
    { name: 'ADMIN' },
    { name: 'EDIT_DATA' },
    { name: 'EXPORT_DATA' },
    { name: 'VIEW_DATA' },
    { name: 'UPLOAD_DATA' },
    { name: 'DELETE_DATA' }
  ];

  constructor(
    private messageService: MessageService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.loadUserProfile();
    this.checkIfAdmin();
  }

  loadUserProfile(): void {
    this.isLoading = true;
    this.userService.getCurrentUser().subscribe({
      next: (userData) => {
        // Map backend user data to frontend User interface
        this.user = {
          username: userData.username,
          email: userData.email,
          firstName: userData.firstName,
          lastName: userData.lastName,
          avatar: userData.avatar || '/default-avatar.png',
          profiles: userData.profiles || []
        };
        
        this.isLoading = false;
        
        // Pre-populate edit user with current user data
        this.editUser = { ...this.user };
        
        // Log for debugging
        console.log('User profile loaded:', this.user);
        console.log('Avatar URL:', this.user.avatar);
      },
      error: (err) => {
        console.error('Error loading user profile:', err);
        this.messageService.add({ 
          severity: 'error', 
          summary: 'Error', 
          detail: 'Failed to load user profile information' 
        });
        this.isLoading = false;
      }
    });
  }

  checkIfAdmin(): void {
    this.userService.hasProfile('ADMIN').subscribe(isAdmin => {
      this.isAdmin = isAdmin;
    });
  }

  toggleEdit(): void {
    this.isEditing = !this.isEditing;
    if (this.isEditing) {
      this.editUser = { ...this.user };
    }
  }

  onImageSelect(event: any): void {
    const file = event?.files?.[0];
    if (!file) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'No file selected' });
      return;
    }

    const validImageTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp', 'image/svg+xml'];
    const maxSize = 5 * 1024 * 1024; // 5MB

    if (!validImageTypes.includes(file.type)) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Invalid file type. Please select a valid image (JPEG, PNG, GIF, BMP, WebP, or SVG)'
      });
      return;
    }

    if (file.size > maxSize) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'File size exceeds 5MB limit'
      });
      return;
    }

    // Create a FormData object to upload the file
    const formData = new FormData();
    formData.append('file', file);

    // Show loading indicator
    this.messageService.add({ 
      severity: 'info', 
      summary: 'Uploading', 
      detail: 'Uploading avatar image...',
      sticky: true,
      key: 'avatar-upload'
    });

    // Upload the file to the server
    this.userService.uploadAvatar(formData).subscribe({
      next: (response) => {
        // Clear the loading message
        this.messageService.clear('avatar-upload');
        
        // Update the user's avatar with the full URL returned from backend
        this.user.avatar = response.avatarUrl;
        this.editUser.avatar = response.avatarUrl;
        
        console.log('Avatar updated successfully:', response.avatarUrl);
        
        this.messageService.add({ 
          severity: 'success', 
          summary: 'Success', 
          detail: 'Avatar image uploaded successfully' 
        });
      },
      error: (err) => {
        // Clear the loading message
        this.messageService.clear('avatar-upload');
        
        console.error('Error uploading avatar:', err);
        this.messageService.add({ 
          severity: 'error', 
          summary: 'Error', 
          detail: err.error?.message || 'Failed to upload avatar image' 
        });
      }
    });
  }

  saveProfile(): void {
    if (this.editUser.username.trim().length < 3) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Username must be at least 3 characters long' });
      return;
    }

    // Prepare the profile update data
    const profileData = {
      username: this.editUser.username,
      firstName: this.editUser.firstName,
      lastName: this.editUser.lastName
    };
    
    this.userService.updateProfile(profileData).subscribe({
      next: (response) => {
        // Update the local user object with the response data
        this.user = {
          ...this.user,
          username: response.username,
          firstName: response.firstName,
          lastName: response.lastName
        };
        
        this.isEditing = false;
        this.profileUpdated.emit(this.user);
        this.messageService.add({ 
          severity: 'success', 
          summary: 'Success', 
          detail: 'Profile updated successfully' 
        });
      },
      error: (err) => {
        console.error('Error updating profile:', err);
        this.messageService.add({ 
          severity: 'error', 
          summary: 'Error', 
          detail: err.error?.message || 'Failed to update profile' 
        });
      }
    });
  }

  cancelEdit(): void {
    this.isEditing = false;
    this.editUser = { ...this.user };
  }

  changePassword(): void {
    this.isChangingPassword = true;

    if (!this.passwordData.current) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Current password is required' });
      this.isChangingPassword = false;
      return;
    }

    if (!this.passwordData.new || !this.passwordData.confirm) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'New password and confirmation are required' });
      this.isChangingPassword = false;
      return;
    }

    if (this.passwordData.new !== this.passwordData.confirm) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'New passwords do not match' });
      this.isChangingPassword = false;
      return;
    }

    if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,}$/.test(this.passwordData.new)) {
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'New password must be at least 8 characters long and contain letters and numbers'
      });
      this.isChangingPassword = false;
      return;
    }

    const passwordPayload = {
      currentPassword: this.passwordData.current,
      newPassword: this.passwordData.new
    };
    
    this.userService.changePassword(passwordPayload).subscribe({
      next: () => {
        this.showPasswordDialog = false;
        this.passwordData = { current: '', new: '', confirm: '' };
        this.messageService.add({ 
          severity: 'success', 
          summary: 'Success', 
          detail: 'Password changed successfully' 
        });
        this.isChangingPassword = false;
      },
      error: (err) => {
        console.error('Error changing password:', err);
        this.messageService.add({ 
          severity: 'error', 
          summary: 'Error', 
          detail: err.error?.message || 'Failed to change password' 
        });
        this.isChangingPassword = false;
      }
    });
  }

  getProfileSeverity(profileName: string): string {
    if (!profileName) return 'info';
    
    const normalizedName = profileName.toUpperCase();
    
    switch (normalizedName) {
      case 'ADMIN': return 'danger';
      case 'EDITOR': return 'warning';
      case 'DATA_MANAGER': return 'warning';
      case 'DATA_ADMIN': return 'danger';
      case 'USER': return 'info';
      default: return 'success';
    }
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
        console.log('Unknown role:', cleanRole);
        return 'info';
    }
  }
}
