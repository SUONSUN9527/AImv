// 连续对话时前端会把「对话记忆 + 本次要求」拼进 goal 发给后端做上下文延续，
// 但展示给用户时只应显示他本次真正输入的那句，不暴露内部的记忆包裹。
export function extractRawRequest(goal: string | null | undefined): string {
  if (typeof goal !== 'string') {
    return '';
  }
  const match = goal.match(/【本次要求】([\s\S]*)$/);
  return (match ? match[1] : goal).trim();
}
