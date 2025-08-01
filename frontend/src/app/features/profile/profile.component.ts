import { Component, OnInit, inject } from '@angular/core';
import { ProfileInfoComponent } from "./components/profile-info/profile-info.component";
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { CommonModule } from '@angular/common';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { UserService } from '../../core/services/user.service';

import { ProfilesManageComponent } from "./components/profiles-manage/profiles-manage.component";
import { ProfilesAssignmentsComponent } from "./components/profiles-assignments/profiles-assignments.component";

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    ProfileInfoComponent, 
    ProfilesManageComponent,
    ProfilesAssignmentsComponent,
    ToastModule
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
  providers: [DialogService, MessageService]
})
export default class ProfileComponent implements OnInit {
  ref: DynamicDialogRef | undefined;
  dialogService = inject(DialogService);
  messageService = inject(MessageService);
  
  isAdmin: boolean = false;
  isLoading: boolean = true;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.checkUserRole();
  }

  /**
   * Check if the current user has admin role
   */
  checkUserRole(): void {
    this.isLoading = true;
    this.userService.hasProfile('ADMIN').subscribe({
      next: (isAdmin) => {
        this.isAdmin = isAdmin;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error checking user role:', err);
        this.isLoading = false;
      }
    });
  }

  
}