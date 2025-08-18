import { Component, EventEmitter, Output, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { CustomConfirmationService } from '../../../core/services/confirmation.service';
import { SimpleNotificationService } from '../../../core/services/simple-notification.service';
import { ConfirmDialogModule } from 'primeng/confirmdialog';

import { 
  faChartLine, 
  faDatabase, 
  faProjectDiagram, 
  faTasks, 
  faServer,
  faChevronLeft,
  faChevronRight,
  faSquarePollVertical,
  faHome,
  faSignOutAlt,
  faWandMagicSparkles
} from '@fortawesome/free-solid-svg-icons';
import { AuthService } from '../../../core/services/auth.service';
import { AuthApiService } from '../../../features/auth/service/auth-api/auth-api.service';
import { User, UserProfile } from '../../../core/models/user.model';
import { UserService } from '../../../core/services/user.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule, FontAwesomeModule, ConfirmDialogModule],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css'],
  encapsulation: ViewEncapsulation.Emulated,
})
export class SidebarComponent implements OnInit {
  isCollapsed = false;
  @Output() collapsed = new EventEmitter<boolean>();

  // Font Awesome icons
  faChartLine = faChartLine;
  faDatabase = faDatabase;
  faProjectDiagram = faProjectDiagram;
  faTasks = faTasks;
  faServer = faServer;
  faChevronLeft = faChevronLeft;
  faChevronRight = faChevronRight;
  faHome = faHome;
  faSignOutAlt = faSignOutAlt;
  faSquarePollVertical = faSquarePollVertical;
  faWandMagicSparkles = faWandMagicSparkles;
  currentUser: User | null = null;
  userProfiles: UserProfile[] = [];
  avatarUrl: string = 'default-avatar.png';
  currentRoute: string = '';


  constructor(
    private authService: AuthService,
    private authApiService: AuthApiService,
    private userService: UserService,
    private router: Router,
    private confirmationService: CustomConfirmationService,
    private notificationService: SimpleNotificationService
  ) {
        this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: NavigationEnd) => {
        this.currentRoute = event.urlAfterRedirects;
      });
    }
  
  ngOnInit(): void {
    // First get the current user from local storage
    this.currentUser = this.authService.getCurrentUser();
    console.log('Initial user from localStorage:', this.currentUser);
    
    // Get profiles from the user
    this.userProfiles = this.currentUser?.profiles || [];
    console.log('Initial profiles from localStorage:', this.userProfiles);

    // Load the user profile directly from UserService to get the avatar and profiles
    this.loadUserProfile();
  }

  loadUserProfile(): void {
    console.log('Loading user profile for sidebar...');
    
    // First, load user profiles
    this.userService.getUserProfiles().subscribe({
      next: (profiles) => {
        console.log('User profiles received in sidebar:', profiles);
        this.userProfiles = profiles;
      },
      error: (error) => {
        console.error('Error loading user profiles in sidebar:', error);
      }
    });
    
    // Then, load the full user data (including avatar)
    this.userService.getCurrentUser().subscribe({
      next: (userData) => {
        console.log('User data received in sidebar:', userData);
        
        // Update current user data from the auth service
        this.currentUser = this.authService.getCurrentUser();
        
        // Set avatar URL from the user data
        this.avatarUrl = userData.avatar || 'default-avatar.png';
        
        console.log('Avatar URL in sidebar:', this.avatarUrl);
      },
      error: (error) => {
        console.error('Error loading user profile in sidebar:', error);
      }
    });
  }

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed;
    this.collapsed.emit(this.isCollapsed);
  }

  getUserInitials(): string {
    if (!this.currentUser) return 'G';
    
    // Just use the first two characters from the username
    return this.currentUser.username.substring(0, 2).toUpperCase();
  }

  getProfileClass(profileName: string): string {
    // Convert profile name to lowercase for class matching
    return profileName.toLowerCase();
  }

  /**
   * Get the user's avatar URL with fallback
   */
  getUserAvatar(): string {
    return this.avatarUrl || '/default-avatar.png';
  }

  /**
   * Log the user out with confirmation
   */
logout(): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to log out?',
      header: 'Logout Confirmation',
      icon: 'pi pi-sign-out',
      acceptLabel: 'Log Out',
      rejectLabel: 'Cancel',
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary'
    }).then((confirmed) => {
      if (confirmed) {
        this.authApiService.logout().subscribe({
          next: () => {
            console.log('Logged out successfully');
            this.notificationService.showSuccess('Successfully logged out', 'Goodbye!');
            this.router.navigate(['/login']);
          },
          error: (error) => {
            console.error('Logout error:', error);
            this.notificationService.showError('Error during logout', 'Error');
            // Still navigate to login as we've already cleared auth locally
            this.router.navigate(['/login']);
          }
        });
      }
    });
}
}