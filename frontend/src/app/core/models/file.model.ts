export interface File {
  id: string;
  originalName: string;
  storedName: string;
  filePath: string;
  fileType: string;
  fileSize: number;
  description?: string;
  uploadedAt: Date;
  status: 'UPLOADED' | 'PROCESSING' | 'PROCESSED' | 'ERROR';
  errorMessage?: string;
  datasetId?: string; // New field for immediate dataset creation
} 