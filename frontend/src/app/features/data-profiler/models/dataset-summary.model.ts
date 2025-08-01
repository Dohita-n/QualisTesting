export interface DatasetSummary {
  id: string;
  name: string;
  createdAt: string;
  columnCount: number;
  rowCount: number;
  columns: DatasetColumn[];
  qualityScore?: number; // Derived
}

export interface DatasetColumn {
  id: string;
  name: string;
  dataType: string;
  nullCount: number;
  uniqueCount: number;
} 