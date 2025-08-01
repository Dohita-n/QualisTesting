export interface User {
  id: string;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  role?: string;
  profiles?: UserProfile[];
  createdAt?: Date;
  updatedAt?: Date;
  avatar?: string;
}

export interface UserProfile {
  id: string;
  name: string;
  description?: string;
  roleNames?: string[];
} 