import { File } from './file.model';

export interface Dataset {
  columns: any;
  id: string;
  name: string;
  description?: string;
  createdAt: Date;
  modifiedAt: Date;
  file?: File;
  rowCount: number;
  columnCount: number;
  status: string;
}

export interface DatasetColumn {
  id: string;
  dataset: Dataset;
  name: string;
  dataType: string;
  position: number;
  isKey: boolean;
  descriptiveStats?: any;
  nullCount?: number;
  uniqueCount?: number;
  minValue?: any;
  maxValue?: any;
  meanValue?: any;
  medianValue?: any;
  stdDevValue?: any;
}

export interface DatasetRow {
  id: string;
  dataset: Dataset;
  rowNumber: number;
  data: Record<string, any>;
}

export interface DatasetSummary {
  id: string;
  dataset: Dataset;
  rowCount: number;
  columnCount: number;
  dataQualityScore?: number;
  missingValuesPercentage?: number;
  analysisDate?: Date;
  nullPercentage: number;
  columns: {
    name: string;
    dataType: string;
    nullCount: number;
    nullPercentage: number;
    uniqueCount: number;
    uniquePercentage: number;
  }[];
} 