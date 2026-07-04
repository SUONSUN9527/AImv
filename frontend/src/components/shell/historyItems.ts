import type { ChainRun, Project } from '../../types/api';

export type SidebarHistoryItem = {
  id: string;
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

export function buildSidebarHistoryItems(
  recentRuns: ChainRun[],
  recentProjects: Project[],
  titleOverrides: Record<string, string> = {},
  options: SidebarHistoryOptions = {}
): SidebarHistoryItem[] {
  const items: SidebarHistoryItem[] = recentRuns.map((run) => ({
    id: run.chainRunId,
    title: displayTitle(run.chainRunId, run.userGoal, titleOverrides),
    ...historyStatus(run.status),
    to: `/workspace/${run.chainRunId}`,
    dedupeKey: run.userGoal.trim(),
    sortAt: run.createdAt,
    projectId: run.projectId
  }));
  const seenTitles = new Set(items.map((item) => item.dedupeKey).filter(Boolean));
  const seenProjectIds = new Set(items.map((item) => item.projectId).filter(Boolean));

  recentProjects.forEach((project) => {
    const title = projectTitle(project);
    if (!title || seenTitles.has(title) || seenProjectIds.has(project.projectId)) {
      return;
    }
    seenTitles.add(title);
    // 后端 /api/projects 现在返回 latestChainRunId：有值即可点击直达对应 workspace 链路；
    // 仍为空（项目建了但从未跑过链路）时保留提示、不可点。
    items.push({
      id: project.projectId,
      title: displayTitle(project.projectId, title, titleOverrides),
      statusState: 'stopped',
      statusAriaLabel: '无任务状态',
      dedupeKey: title,
      sortAt: project.createdAt,
      projectId: project.projectId,
      to: project.latestChainRunId ? `/workspace/${project.latestChainRunId}` : undefined,
      hint: project.latestChainRunId ? undefined : '该项目还没有生成记录'
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

function historyStatus(status: string): Pick<SidebarHistoryItem, 'statusState' | 'statusAriaLabel'> {
  if (status === 'SUCCEEDED') {
    return { statusState: 'complete', statusAriaLabel: '任务完成' };
  }
  if (status === 'FAILED' || status === 'CANCELLED') {
    return { statusState: 'stopped', statusAriaLabel: '任务停止' };
  }
  return { statusState: 'running', statusAriaLabel: '任务进行中' };
}

function sortTime(value: string) {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
