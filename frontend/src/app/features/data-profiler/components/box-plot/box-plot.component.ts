import { Component, Input, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';


@Component({
  selector: 'app-box-plot',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './box-plot.component.html',
  styleUrls: ['./box-plot.component.css'],
  encapsulation: ViewEncapsulation.Emulated
})
export class BoxPlotComponent {
  @Input() min: number = 0;
  @Input() max: number = 100;
  @Input() q1?: number;
  @Input() q3?: number;
  @Input() median: number = 50;
  @Input() mean: number = 50;
  @Input() stdDev: number = 10;
  @Input() height: number = 200;
  
  getPercentPosition(value: number): number {
    const range = this.max - this.min;
    if (range <= 0) return 50;
    return ((value - this.min) / range) * 100;
  }
  
  getBoxWidth(): number {
    // If we have Q1 and Q3, use them for the box
    if (this.q1 !== undefined && this.q3 !== undefined) {
      const range = this.max - this.min;
      if (range <= 0) return 50;
      return ((this.q3 - this.q1) / range) * 100;
    }
    
    // Otherwise, approximate IQR using stdDev
    const iqrApprox = this.stdDev * 1.35; // Approximation of IQR
    const range = this.max - this.min;
    if (range <= 0) return 50;
    return (iqrApprox / range) * 100;
  }
} 