import type { ChainRun, Project } from '../../types/api';
import { extractRawRequest } from '../../utils/conversation';

export type SidebarHistoryItem = {
  id: string; // = projectId（一段对话的唯一标识，用于重命名/置顶/删除）
  chainRunId?: string; // 该对话最新一次链路 id（用于导航、当前高亮、状态）
  title: string;
  statusState: 'complete' | 'running' | 'stopped';
  statusAriaLabel: string;
  to?: string;
  dedupeKey: string;
  sortAt: string;
  projectId?: string;
  hint?: string;
};

export type SidebarHistoryOptions = {
  pinnedIds?: string[];
  hiddenIds?: string[];
};

// 一段「对话」= 同一个 project 下的多次链路。侧边栏按 project 分组、每段对话只出一条，
// 名称用 project.title（建会话时按首条消息定的、存库里，天然稳定，续对话不会变），
// 没有 project.title 时兜底用首条 run 的原始请求。导航/状态用最新一次链路。
export function buildSidebarHistoryItems(
  recentRuns: ChainRun[],
  recentProjects: Project[],
  titleOverrides: Record<string, string> = {},
  options: SidebarHistoryOptions = {}
): SidebarHistoryItem[] {
  const projectTitleById = new Map(recentProjects.map((project) => [project.projectId, projectTitle(project)]));
  const projectCreatedById = new Map(recentProjects.map((project) => [project.projectId, project.createdAt]));
  const latestRunIdByProject = new Map(recentProjects.map((project) => [project.projectId, project.latestChainRunId]));
  // 服务端真实状态（权威）：优先于本地缓存的 run 状态，避免链路已完成后侧边栏仍显示「生成中」。
  const projectStatusById = new Map(
    recentProjects.map((project) => [project.projectId, project.latestChainRunStatus])
  );

  const runsByProject = new Map<string, ChainRun[]>();
  recentRuns.forEach((run) => {
    const list = runsByProject.get(run.projectId) ?? [];
    list.push(run);
    runsByProject.set(run.projectId, list);
  });

  const projectIds = new Set<string>([...runsByProject.keys(), ...recentProjects.map((project) => project.projectId)]);
  const items: SidebarHistoryItem[] = [];
  projectIds.forEach((projectId) => {
    const runs = (runsByProject.get(projectId) ?? [])
      .slice()
      .sort((left, right) => sortTime(left.createdAt) - sortTime(right.createdAt));
    const firstRun = runs[0];
    const latestRun = runs[runs.length - 1];
    const stableName = projectTitleById.get(projectId) || (firstRun ? extractRawRequest(firstRun.userGoal) : '');
    if (!stableName) {
      return;
    }
    const latestChainRunId = latestRun?.chainRunId ?? latestRunIdByProject.get(projectId) ?? undefined;
    // 服务端状态优先；无（如刚新建、projects 尚未刷新）时回退本地缓存的 run 状态。
    const effectiveStatus = projectStatusById.get(projectId) ?? latestRun?.status;
    items.push({
      id: projectId,
      chainRunId: latestChainRunId ?? undefined,
      title: displayTitle(projectId, stableName, titleOverrides),
      ...historyStatus(effectiveStatus ?? undefined, Boolean(latestChainRunId)),
      to: latestChainRunId ? `/workspace/${latestChainRunId}` : undefined,
      dedupeKey: projectId,
      sortAt: latestRun?.createdAt ?? projectCreatedById.get(projectId) ?? '',
      projectId,
      hint: latestChainRunId ? undefined : '该对话还没有生成记录'
    });
  });

  const pinnedIds = options.pinnedIds ?? [];
  const hiddenIds = new Set(options.hiddenIds ?? []);
  const pinnedOrder = new Map(pinnedIds.map((id, index) => [id, index]));
  return items
    .filter((item) => !hiddenIds.has(item.id))
    .sort((left, right) => {
      const leftPinned = pinnedOrder.get(left.id);
      const rightPinned = pinnedOrder.get(right.id);
      if (leftPinned !== undefined || rightPinned !== undefined) {
        return (leftPinned ?? Number.MAX_SAFE_INTEGER) - (rightPinned ?? Number.MAX_SAFE_INTEGER);
      }
      return sortTime(right.sortAt) - sortTime(left.sortAt);
    });
}

function projectTitle(project: Project) {
  return project.title.trim() || project.goal.trim();
}

function displayTitle(id: string, fallback: string, titleOverrides: Record<string, string>) {
  return titleOverrides[id]?.trim() || fallback;
}

function historyStatus(
  status: string | undefined,
  hasRun: boolean
): Pick<SidebarHistoryItem, 'statusState' | 'statusAriaLabel'> {
  if (status === 'SUCCEEDED') {
    return { statusState: 'complete', statusAriaLabel: '任务完成' };
  }
  if (status === 'FAILED' || status === 'CANCELLED') {
    return { statusState: 'stopped', statusAriaLabel: '任务停止' };
  }
  if (!status) {
    return hasRun
      ? { statusState: 'complete', statusAriaLabel: '任务完成' }
      : { statusState: 'stopped', statusAriaLabel: '无任务状态' };
  }
  return { statusState: 'running', statusAriaLabel: '任务进行中' };
}

function sortTime(value: string) {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
