export interface ColumnTypeChange {
  columnId: string;
  newDataType: string;
  decimalPrecision?: number;
  decimalScale?: number;
} 