export interface TransformationStep {
  id: string;
  transformationType: string;
  parameters: any;
  appliedAt: Date;
  active: boolean;
  sequenceOrder: number;
  previousStep?: TransformationStep;
} 