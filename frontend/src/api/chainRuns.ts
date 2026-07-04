import { apiRequest } from './http';
import type { ApiSelectionSnapshot, ChainRun, ChainType, ExternalJob } from '../types/api';

export function startChainRun(projectId: string, chainType: ChainType, userGoal: string): Promise<ChainRun> {
  const suffix = chainType === 'IMAGE' ? 'image-chain-runs' : 'video-chain-runs';

  return apiRequest<ChainRun>(`/api/projects/${projectId}/${suffix}`, {
    method: 'POST',
    body: JSON.stringify({ userGoal })
  });
}

export function getChainRun(chainRunId: string): Promise<ChainRun> {
  return apiRequest<ChainRun>(`/api/chain-runs/${chainRunId}`);
}

export function getExternalJobs(chainRunId: string): Promise<ExternalJob[]> {
  return apiRequest<ExternalJob[]>(`/api/chain-runs/${chainRunId}/external-jobs`);
}

export function getApiSelectionSnapshots(chainRunId: string): Promise<ApiSelectionSnapshot[]> {
  return apiRequest<ApiSelectionSnapshot[]>(`/api/chain-runs/${chainRunId}/api-selection-snapshot`);
}

export function cancelChainRun(chainRunId: string): Promise<ChainRun> {
  return apiRequest<ChainRun>(`/api/chain-runs/${chainRunId}:cancel`, {
    method: 'POST'
  });
}

export function redoStage(stageRunId: string): Promise<ChainRun> {
  return apiRequest<ChainRun>(`/api/stage-runs/${stageRunId}:redo`, {
    method: 'POST'
  });
}
