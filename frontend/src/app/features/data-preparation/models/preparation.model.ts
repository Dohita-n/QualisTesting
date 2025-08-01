import { TransformationStep } from './transformation-step.model';
import { Dataset } from '../../../core/models/dataset.model';

export interface Preparation {
  id: string;
  name: string;
  description?: string;
  createdAt: Date;
  modifiedAt: Date;
  sourceDataset: Dataset;
  outputDataset?: Dataset;
  transformationSteps: TransformationStep[];
  status: 'DRAFT' | 'PROCESSING' | 'EXECUTED' | 'ERROR';
  createdBy?: {
    id: string;
    username: string;
  };
} 