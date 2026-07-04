import { apiRequest } from './http';

// 让后端用 LLM 语义压缩多轮对话为「记忆」；失败时调用方回退本地启发式压缩。
export function buildConversationMemory(requests: string[]): Promise<{ memory: string }> {
  return apiRequest<{ memory: string }>('/api/conversation-memory', {
    method: 'POST',
    body: JSON.stringify({ requests })
  });
}
