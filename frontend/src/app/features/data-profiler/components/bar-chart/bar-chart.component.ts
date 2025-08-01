import { Component, Input, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartDataPoint } from '../../../../core/models/column-statistics.model';

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bar-chart.component.html',
  styleUrls: ['./bar-chart.component.css'],
  encapsulation: ViewEncapsulation.Emulated
})
export class BarChartComponent {
  @Input() data: ChartDataPoint[] = [];
  @Input() height: number = 200;
  
  getBarHeight(item: ChartDataPoint): number {
    const maxValue = Math.max(...this.data.map(d => d.value));
    return maxValue > 0 ? (item.value / maxValue) * 90 : 0; // 90% max height for aesthetics
  }
} 