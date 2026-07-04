import { apiRequest } from './http';
import type { Project } from '../types/api';

export function createProject(title: string, goal: string): Promise<Project> {
  return apiRequest<Project>('/api/projects', {
    method: 'POST',
    body: JSON.stringify({ title, goal })
  });
}

export function listProjects(): Promise<Project[]> {
  return apiRequest<Project[]>('/api/projects');
}
