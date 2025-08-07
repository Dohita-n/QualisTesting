import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastModule],
  template: `
    <p-toast 
      position="top-right" 
      [baseZIndex]="1000"
      [autoZIndex]="true"
      [showTransformOptions]="'translateX(100%)'"
      [hideTransformOptions]="'translateX(100%)'"
      [showTransitionOptions]="'400ms ease-out'"
      [hideTransitionOptions]="'250ms ease-in'"
    />
    <router-outlet></router-outlet>
  `,
  styles: [],
  providers: [MessageService]
})
export class AppComponent implements OnInit {
  title = 'QualisDS';
  
  constructor(private authService: AuthService) {}
  
  ngOnInit() {
    // Initialize authentication on app start
    if (this.authService.isAuthenticated()) {
      console.log('User is authenticated, fetching profile data...');
      this.authService.refreshUserData().subscribe({
        next: (userData) => {
          console.log('User profile data loaded successfully:', userData);
        },
        error: (err) => {
          console.error('Failed to load user profile data:', err);
        }
      });
    } else {
      console.log('User is not authenticated');
    }
  }
}
