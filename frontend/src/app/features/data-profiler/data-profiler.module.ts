import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Routes } from '@angular/router';

import { DataProfilerComponent } from './data-profiler.component';
import { BarChartComponent } from './components/bar-chart/bar-chart.component';
import { BoxPlotComponent } from './components/box-plot/box-plot.component';
import { SkewnessGaugeComponent } from './components/skewness-gauge/skewness-gauge.component';

const routes: Routes = [
  { path: ':id', component: DataProfilerComponent }
];

@NgModule({
  declarations: [],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule.forChild(routes),
    // Standalone components
    DataProfilerComponent,
    BarChartComponent,
    BoxPlotComponent,
    SkewnessGaugeComponent
  ]
})
export class DataProfilerModule { }
