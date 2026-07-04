import { apiRequest } from './http';
import type { ApiConfigSlot, ApiKeySummary, ChainType } from '../types/api';

export function listApiConfigs(chainType: ChainType): Promise<ApiConfigSlot[]> {
  return apiRequest<ApiConfigSlot[]>(`/api/api-configs?chainType=${chainType}`);
}

export function addApiKey(
  chainType: ChainType,
  capabilityType: string,
  input: { provider: string; label: string; apiKey: string; model?: string }
): Promise<ApiKeySummary> {
  return apiRequest<ApiKeySummary>(`/api/api-configs/${chainType}/${capabilityType}/keys`, {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export function verifyApiKey(apiKeyId: string): Promise<ApiKeySummary> {
  return apiRequest<ApiKeySummary>(`/api/api-keys/${apiKeyId}:verify`, { method: 'POST' });
}

export function selectApiKey(apiKeyId: string): Promise<ApiKeySummary> {
  return apiRequest<ApiKeySummary>(`/api/api-keys/${apiKeyId}:select`, { method: 'POST' });
}

export function deleteApiKey(apiKeyId: string): Promise<void> {
  return apiRequest<void>(`/api/api-keys/${apiKeyId}`, { method: 'DELETE' });
}
