import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="not-found-container">
      <h1>404</h1>
      <h2>Page Not Found</h2>
      <p>The page you are looking for does not exist or has been moved.</p>
      <a routerLink="/dashboard">Go to Home</a>
    </div>
  `,
  styles: [`
    .not-found-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 80vh;
      text-align: center;
    }
    
    h1 {
      font-size: 6rem;
      margin: 0;
      color: #3b82f6;
    }
    
    h2 {
      font-size: 2rem;
      margin: 0 0 1rem 0;
      color: #4b5563;
    }
    
    p {
      margin-bottom: 2rem;
      color: #6b7280;
    }
    
    a {
      background-color: #3b82f6;
      color: white;
      padding: 0.75rem 1.5rem;
      border-radius: 0.375rem;
      text-decoration: none;
      font-weight: 500;
      transition: background-color 0.2s;
    }
    
    a:hover {
      background-color: #2563eb;
    }
  `]
})
export class NotFoundComponent {} 