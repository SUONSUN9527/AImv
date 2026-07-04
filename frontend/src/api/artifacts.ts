import { apiRequest } from './http';
import type { Artifact } from '../types/api';

export function listArtifacts(): Promise<Artifact[]> {
  return apiRequest<Artifact[]>('/api/artifacts');
}
