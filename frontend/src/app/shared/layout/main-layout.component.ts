import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SidebarComponent } from './sidebar/sidebar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar (collapsed)="onSidebarCollapsed($event)"></app-sidebar>
      <main class="main-content" [class.main-content-expanded]="isSidebarCollapsed">
        <div class="content-wrapper">
          <router-outlet></router-outlet>
        </div>
      </main>
    </div>
  `,
  styles: [`
    .app-container {
      display: flex;
      height: 100vh;
      overflow: hidden;
    }
    
    .main-content {
      flex: 1;
      margin-left: 250px;
      padding: 20px;
      overflow-y: auto;
      transition: margin-left 0.3s ease;
    }
    
    .main-content-expanded {
      margin-left: 70px;
    }
    
    .content-wrapper {
      max-width: 1400px;
      margin: 0 auto;
    }
  `]
})
export class MainLayoutComponent {
  isSidebarCollapsed = false;
  
  onSidebarCollapsed(collapsed: boolean) {
    this.isSidebarCollapsed = collapsed;
  }
} 