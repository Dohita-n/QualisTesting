import { Component, OnInit, inject, ViewChild } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule, Table } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MenuModule } from 'primeng/menu';
import { PopoverModule } from 'primeng/popover';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../../../environements/environment';
import { AuthService } from '../../../../core/services/auth.service';
import { firstValueFrom } from 'rxjs';
import { AvatarModule } from 'primeng/avatar';
import { DialogModule } from 'primeng/dialog';
import { MultiSelectModule } from 'primeng/multiselect';
import { FormsModule } from '@angular/forms';
import { TooltipModule } from 'primeng/tooltip';

interface UserProfile {
    id: string;
    name: string;
    description?: string;
    roleNames?: string[];
}

interface User {
    id: string;
    username: string;
    email: string;
    firstName?: string;
    lastName?: string;
    avatarUrl?: string;
    status: string;
    profiles: UserProfile[];
    createdAt: string;
}

@Component({
    selector: 'app-profiles-manage',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        InputTextModule, 
        ButtonModule, 
        CardModule, 
        TableModule, 
        TagModule, 
        MenuModule,
        PopoverModule,
        ConfirmDialogModule,
        ToastModule,
        AvatarModule,
        DialogModule,
        MultiSelectModule,
        TooltipModule
    ],
    templateUrl: './profiles-manage.component.html',
    styleUrl: './profiles-manage.component.css',
    providers: [DialogService, ConfirmationService, MessageService]
})
export class ProfilesManageComponent implements OnInit {
    @ViewChild('dt1') dt1: Table | undefined;
    
    ref: DynamicDialogRef | undefined;
    dialogService = inject(DialogService);
    confirmationService = inject(ConfirmationService);
    messageService = inject(MessageService);
    
    private apiUrl = environment.apiUrl;
    private http = inject(HttpClient);
    private authService = inject(AuthService);
    
    users: User[] = [];
    loading: boolean = true;
    selectedUser: User | null = null;
    showProfileDialog: boolean = false;
    availableProfiles: any[] = [];
    selectedProfiles: string[] = [];
    loadingProfiles: boolean = false;
    
    ngOnInit() {
        this.fetchUsers();
        this.fetchAvailableProfiles();
    }
    
    onRowSelect(event: any) {
        this.selectedUser = event.data;
    }
    
    onRowUnselect() {
        this.selectedUser = null;
    }
    
    openProfileDialog(user: User) {
        this.selectedUser = user;
        this.selectedProfiles = user.profiles?.map(p => p.id) || [];
        this.showProfileDialog = true;
    }
    
    closeProfileDialog() {
        this.showProfileDialog = false;
        this.selectedUser = null;
        this.selectedProfiles = [];
    }
    
    async fetchAvailableProfiles() {
        try {
            this.loadingProfiles = true;
            const headers = this.getAuthHeaders();
            const profiles = await firstValueFrom(this.http.get<any[]>(`${this.apiUrl}/admin/profiles`, { headers }));
            this.availableProfiles = profiles;
        } catch (error) {
            console.error('Error fetching profiles:', error);
            this.messageService.add({
                severity: 'error',
                summary: 'Error',
                detail: 'Failed to load available profiles'
            });
        } finally {
            this.loadingProfiles = false;
        }
    }
    
    async saveProfileChanges() {
        if (!this.selectedUser) return;
        
        try {
            const headers = this.getAuthHeaders();
            const userId = this.selectedUser.id;
            
            // Get current profiles
            const currentProfileIds = this.selectedUser.profiles?.map(p => p.id) || [];
            
            // Profiles to add
            const profilesToAdd = this.selectedProfiles.filter(id => !currentProfileIds.includes(id));
            
            // Profiles to remove
            const profilesToRemove = currentProfileIds.filter(id => !this.selectedProfiles.includes(id));
            
            // Add new profiles
            for (const profileId of profilesToAdd) {
                await firstValueFrom(this.http.post(
                    `${this.apiUrl}/admin/users/${userId}/profiles/${profileId}`, 
                    {}, 
                    { headers }
                ));
            }
            
            // Remove profiles
            for (const profileId of profilesToRemove) {
                await firstValueFrom(this.http.delete(
                    `${this.apiUrl}/admin/users/${userId}/profiles/${profileId}`,
                    { headers }
                ));
            }
            
            // Refresh the user data
            await this.fetchUsers();
            
            this.messageService.add({
                severity: 'success',
                summary: 'Success',
                detail: 'User profiles updated successfully'
            });
            
            this.closeProfileDialog();
        } catch (error) {
            console.error('Error saving profile changes:', error);
            this.messageService.add({
                severity: 'error',
                summary: 'Error',
                detail: 'Failed to update user profiles'
            });
        }
    }
    
    onFilterInput(event: Event): void {
        const target = event.target as HTMLInputElement;
        if (target && target.value !== undefined) {
            this.dt1?.filterGlobal(target.value, 'contains');
        }
    }
    
    async fetchUsers() {
        try {
            this.loading = true;
            const headers = this.getAuthHeaders();
            const users = await firstValueFrom(this.http.get<User[]>(`${this.apiUrl}/admin/users`, { headers }));
            this.users = users;
        } catch (error) {
            console.error('Error fetching users:', error);
            this.messageService.add({
                severity: 'error',
                summary: 'Error',
                detail: 'Failed to load users'
            });
        } finally {
            this.loading = false;
        }
    }
    
    getUserFullName(user: User): string {
        if (user.firstName && user.lastName) {
            return `${user.firstName} ${user.lastName}`;
        } else if (user.firstName) {
            return user.firstName;
        } else if (user.lastName) {
            return user.lastName;
        }
        return user.username;
    }
    
    getMainProfile(user: User): string {
        if (!user.profiles || user.profiles.length === 0) {
            return 'No profile';
        }
        return user.profiles[0].name;
    }
    
    getAllProfiles(user: User): string[] {
        if (!user.profiles || user.profiles.length === 0) {
            return ['No profile'];
        }
        return user.profiles.map(p => p.name);
    }
    
    getSeverityStatus(status: string) {
        switch (status) {
            case 'active':
                return 'success';
            case 'locked':
                return 'warning';
            case 'disabled':
                return 'danger';
            default: 
                return 'info';
        }
    }
    
    getSeverityUserType(profileName: string) {
        switch (profileName.toUpperCase()) {
            case 'ADMIN':
                return 'danger';
            case 'DATA_MANAGER':
                return 'warning';
            case 'EDITOR':
                return 'success';
            default: 
                return 'info';
        }
    }

    confirmDeleteUser(user: User, event: Event) {
        event.stopPropagation(); // Prevent row selection
        
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: `Are you sure you want to delete the user "${user.username}"?`,
            header: 'Confirmation',
            closable: true,
            closeOnEscape: true,
            icon: 'pi pi-exclamation-triangle',
            rejectButtonProps: {
                label: 'Cancel',
                severity: 'secondary',
                outlined: true,
            },
            acceptButtonProps: {
                label: 'Confirm',
            },
            accept: () => {
                this.deleteUser(user.id);
            },
            reject: () => {
                this.messageService.add({ 
                    severity: 'info', 
                    summary: 'Cancelled', 
                    detail: 'User deletion cancelled', 
                    life: 3000 
                });
            },
        });
    }

    confirmDelete(event: Event) {
        // Store the target element to determine which user to delete
        const element = event.target as HTMLElement;
        const row = element.closest('tr');
        if (!row) return;
        
        // Find the user data from the row
        const userData = this.users.find(user => {
            const rowElement = this.dt1?.el.nativeElement.querySelector(`tr[data-user-id="${user.id}"]`);
            return rowElement === row;
        });
        
        if (!userData) {
            this.messageService.add({
                severity: 'error',
                summary: 'Error',
                detail: 'Could not identify the user to delete',
                life: 3000
            });
            return;
        }
        
        this.confirmationService.confirm({
            target: event.target as EventTarget,
            message: `Are you sure you want to delete the user "${userData.username}"?`,
            header: 'Confirmation',
            closable: true,
            closeOnEscape: true,
            icon: 'pi pi-exclamation-triangle',
            rejectButtonProps: {
                label: 'Cancel',
                severity: 'secondary',
                outlined: true,
            },
            acceptButtonProps: {
                label: 'Confirm',
            },
            accept: () => {
                this.deleteUser(userData.id);
            },
            reject: () => {
                this.messageService.add({ 
                    severity: 'info', 
                    summary: 'Cancelled', 
                    detail: 'User deletion cancelled', 
                    life: 3000 
                });
            },
        });
    }
    
    async deleteUser(userId: string) {
        try {
            const headers = this.getAuthHeaders();
            await firstValueFrom(this.http.delete(`${this.apiUrl}/admin/users/${userId}`, { headers }));
            
            // Remove user from the local array
            this.users = this.users.filter(user => user.id !== userId);
            
            this.messageService.add({ 
                severity: 'success', 
                summary: 'Success', 
                detail: 'User deleted successfully', 
                life: 3000 
            });
        } catch (error: any) {
            console.error('Error deleting user:', error);
            this.messageService.add({ 
                severity: 'error', 
                summary: 'Error', 
                detail: error.message || 'Failed to delete user', 
                life: 3000 
            });
        }
    }

    private getAuthHeaders(): HttpHeaders {
        const token = this.authService.getToken();
        return new HttpHeaders({
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        });
    }
}

