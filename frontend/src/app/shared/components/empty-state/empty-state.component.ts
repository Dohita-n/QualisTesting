import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './empty-state.component.html',
  styleUrls: ['./empty-state.component.css']
})
export class EmptyStateComponent {
  @Input() title: string = 'No data available';
  @Input() message: string = 'There is no data to display.';
  @Input() actionText: string = '';
  @Input() actionRoute: string = '';
  @Input() icon: 'document' | 'data' | 'upload' | 'search' = 'document';
  @Output() actionClick = new EventEmitter<void>();
} 