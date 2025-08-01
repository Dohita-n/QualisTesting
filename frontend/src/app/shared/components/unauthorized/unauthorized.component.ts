import { Component, OnInit } from '@angular/core';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-unauthorized',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="unauthorized-container">
      <div class="card">
        <div class="card-body text-center">
          <h1 class="display-1">403</h1>
          <h2 class="mb-4">Access Denied</h2>
          <p class="lead mb-4">
            You don't have the required permissions to access this resource.
          </p>
          <p *ngIf="requiredPermission" class="text-muted mb-4">
            Required permission: <strong>{{ requiredPermission }}</strong>
          </p>
          <p *ngIf="userRoles.length > 0" class="text-muted mb-4">
            Your current roles: <strong>{{ userRoles.join(', ') }}</strong>
          </p>
          <a routerLink="/dashboard" class="btn btn-primary me-2">Go to Dashboard</a>
          <button *ngIf="isAuthenticated" (click)="logout()" class="btn btn-outline-secondary">
            Login as Different User
          </button>
          <a *ngIf="!isAuthenticated" routerLink="/login" class="btn btn-outline-secondary">
            Login
          </a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .unauthorized-container {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 100vh;
      background-color: #f8f9fa;
    }
    .card {
      max-width: 500px;
      border-radius: 10px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }
    h1 {
      color: #dc3545;
      font-weight: bold;
    }
  `]
})
export class UnauthorizedComponent implements OnInit {
  requiredPermission: string | null = null;
  userRoles: string[] = [];
  isAuthenticated: boolean = false;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    // Check if there's information about the required permission in the query params
    this.route.queryParams.subscribe(params => {
      this.requiredPermission = params['requiredPermission'] || null;
    });

    // Get authentication status
    this.isAuthenticated = this.authService.isAuthenticated();

    // Get user roles if authenticated
    if (this.isAuthenticated) {
      // Available roles in the system that we check against
      const availableRoles = ['ADMIN', 'VIEW_DATA', 'EDIT_DATA', 'UPLOAD_DATA', 'EXPORT_DATA', 'DELETE_DATA'];
      
      // Filter to only include roles the user has
      this.userRoles = availableRoles.filter(role => this.authService.hasRole(role));
    }
  }

  logout(): void {
    // Clear authentication and redirect to login
    this.authService.clearAuth();
    this.router.navigate(['/login']);
  }
} 