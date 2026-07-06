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

/** 删除一段对话及其全部链路/知识数据（后端级联删除）。 */
export function deleteProject(projectId: string): Promise<void> {
  return apiRequest<void>(`/api/projects/${projectId}`, { method: 'DELETE' });
}

/** 置顶/取消置顶一段对话。 */
export function setProjectPinned(projectId: string, pinned: boolean): Promise<void> {
  return apiRequest<void>(`/api/projects/${projectId}/pin`, {
    method: 'PUT',
    body: JSON.stringify({ pinned })
  });
}
