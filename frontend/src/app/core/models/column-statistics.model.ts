export interface ColumnStatistics {
  id: string;
  name: string;
  dataType: string;
  nullCount: number;
  uniqueCount: number;
  min?: string;
  max?: string;
  mean?: number;
  median?: number; 
  stdDev?: number;
  frequentValues?: {[key: string]: number};
  // Derived metrics
  skewness?: number;
  valueDistribution?: ChartDataPoint[];
  validationMetrics?: ValidationMetrics;
}

export interface ChartDataPoint {
  name: string;
  value: number;
  percent: number;
}

export interface ValidationMetrics {
  validCount: number;
  invalidCount: number;
  emptyCount: number;
  totalCount: number;
  validPercent: number;
  invalidPercent: number;
  emptyPercent: number;
  pattern: string;
  lastValidated?: Date;
} 