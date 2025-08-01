import { Component, Input, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skewness-gauge',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './skewness-gauge.component.html',
  styleUrls: ['./skewness-gauge.component.css'],
  encapsulation: ViewEncapsulation.Emulated
})
export class SkewnessGaugeComponent {
  @Input() skewness: number = 0;
  @Input() height: number = 200;
  
  getNeedleRotation(): string {
    // Convert skewness to angle (degrees)
    // Range: typically -2 to +2 maps to -90° to 90°
    const clampedSkewness = Math.max(-3, Math.min(3, this.skewness));
    const angle = (clampedSkewness / 3) * 90;
    return `rotate(${angle}deg)`;
  }
  
  getSkewnessDescription(): string {
    const absSkew = Math.abs(this.skewness);
    
    if (absSkew < 0.5) {
      return 'The data is approximately symmetric';
    }
    
    if (absSkew < 1) {
      return this.skewness < 0 
        ? 'The data is slightly negatively skewed' 
        : 'The data is slightly positively skewed';
    }
    
    return this.skewness < 0 
      ? 'The data is significantly negatively skewed' 
      : 'The data is significantly positively skewed';
  }
} 