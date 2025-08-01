export interface Profile {
  id?: string;
  name: string;
  description?: string;
  roles: string[]; // Role names for display
  roleIds?: string[]; // Role UUIDs for API calls
}

export interface Role {
  id?: string;
  name: string;
  description?: string;
} 