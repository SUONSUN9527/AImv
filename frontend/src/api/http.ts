import type { ApiEnvelope } from '../types/api';

const API_BASE_URL = import.meta.env.VITE_AIMV_API_BASE_URL ?? 'http://127.0.0.1:8081';

export class ApiClientError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {})
    }
  });
  const envelope = (await response.json()) as ApiEnvelope<T>;

  if (!response.ok || !envelope.success) {
    throw new ApiClientError(envelope.error?.code ?? 'REQUEST_FAILED',
      envelope.error?.message ?? '请求失败');
  }

  return envelope.data;
}
