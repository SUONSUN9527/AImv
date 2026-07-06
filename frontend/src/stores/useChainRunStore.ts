import { defineStore } from 'pinia';
import {
  cancelChainRun,
  getApiSelectionSnapshots,
  getChainRun,
  getExternalJobs,
  redoStage,
  startChainRun
} from '../api/chainRuns';
import { buildConversationMemory } from '../api/conversation';
import { createProject } from '../api/projects';
import { extractRawRequest } from '../utils/conversation';
import type { ApiSelectionSnapshot, ChainRun, ChainRunStatus, ChainType, ExternalJob } from '../types/api';

const TERMINAL_STATUSES: ChainRunStatus[] = [
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
  'WAITING_USER',
  'WAITING_CAPABILITY',
  'WAITING_REVIEW'
];

const POLL_INTERVAL_MS = 1500;
const RECENT_RUNS_STORAGE_KEY = 'aimv.recentChainRuns';
const MAX_RECENT_RUNS = 20;

function browserStorage() {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}

function isStoredChainRun(value: unknown): value is ChainRun {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const run = value as Partial<ChainRun>;
  return Boolean(
    typeof run.chainRunId === 'string' &&
      typeof run.projectId === 'string' &&
      typeof run.chainType === 'string' &&
      typeof run.userGoal === 'string' &&
      typeof run.status === 'string' &&
      Array.isArray(run.stageRuns) &&
      Array.isArray(run.artifacts)
  );
}

function readStoredRecentRuns() {
  const storage = browserStorage();
  if (!storage) {
    return [];
  }
  try {
    const raw = storage.getItem(RECENT_RUNS_STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter(isStoredChainRun).slice(0, MAX_RECENT_RUNS) : [];
  } catch {
    return [];
  }
}

function persistRecentRuns(runs: ChainRun[]) {
  const storage = browserStorage();
  if (!storage) {
    return;
  }
  try {
    storage.setItem(RECENT_RUNS_STORAGE_KEY, JSON.stringify(runs.slice(0, MAX_RECENT_RUNS)));
  } catch {
    // Ignore quota/private-mode failures; the active session state still works.
  }
}

function updateRecentRuns(runs: ChainRun[], chainRun: ChainRun, moveToTop: boolean) {
  const existingIndex = runs.findIndex((run) => run.chainRunId === chainRun.chainRunId);
  if (existingIndex === -1) {
    return moveToTop ? [chainRun, ...runs].slice(0, MAX_RECENT_RUNS) : runs;
  }
  if (moveToTop) {
    return [
      chainRun,
      ...runs.filter((run) => run.chainRunId !== chainRun.chainRunId)
    ].slice(0, MAX_RECENT_RUNS);
  }
  const nextRuns = [...runs];
  nextRuns[existingIndex] = chainRun;
  return nextRuns.slice(0, MAX_RECENT_RUNS);
}

// 上下文压缩阈值（近似模型可用「记忆」预算的字符数）。超过则压缩旧轮次，避免超长上下文。
// 提高阈值：连续对话尽量把外貌/风格锚点原文带全，改善多张图之间的连贯性与样貌一致性。
const MAX_CONTEXT_CHARS = 4000;

// 压缩对话为「记忆」：不超阈值就全量；超了则保留「最初设定」(锚点)+最近若干轮 verbatim，
// 中间旧轮归纳为一句提示——保住主体/风格锚点与近期上下文这两处最关键的信息，不做无脑截断。
function compressConversation(requests: string[], threshold: number): string {
  if (requests.length === 0) {
    return '';
  }
  const full = requests.join(' → ');
  if (full.length <= threshold) {
    return full;
  }
  const first = requests[0];
  const recent: string[] = [];
  let used = first.length;
  for (let index = requests.length - 1; index >= 1; index -= 1) {
    if (used + requests[index].length > threshold) {
      break;
    }
    recent.unshift(requests[index]);
    used += requests[index].length;
  }
  const omitted = requests.length - 1 - recent.length;
  const middle = omitted > 0 ? ` …(中间${omitted}轮已归纳：沿用既定主体与风格)… ` : ' → ';
  return `最初设定：${first}${middle}${recent.join(' → ')}`;
}

export const useChainRunStore = defineStore('chainRuns', {
  state: () => ({
    activeChainType: 'IMAGE' as ChainType,
    activeChainRun: null as ChainRun | null,
    externalJobs: [] as ExternalJob[],
    apiSelectionSnapshots: [] as ApiSelectionSnapshot[],
    recentRuns: readStoredRecentRuns() as ChainRun[],
    loading: false,
    errorMessage: '',
    pollHandle: null as number | null
  }),
  actions: {
    rememberRun(chainRun: ChainRun, moveToTop = true) {
      const nextRuns = updateRecentRuns(this.recentRuns, chainRun, moveToTop);
      this.recentRuns = nextRuns;
      persistRecentRuns(nextRuns);
    },
    // 删除对话时清掉本地缓存的该项目所有链路（recentRuns 存于 localStorage，
    // 不清会导致会话在侧边栏复活）；若正好是当前活动对话则停止轮询并清空。
    forgetProjectRuns(projectId: string) {
      const nextRuns = this.recentRuns.filter((run) => run.projectId !== projectId);
      this.recentRuns = nextRuns;
      persistRecentRuns(nextRuns);
      if (this.activeChainRun?.projectId === projectId) {
        this.stopPolling();
        this.activeChainRun = null;
      }
    },
    async createAndStart(chainType: ChainType, userGoal: string) {
      this.loading = true;
      this.errorMessage = '';
      try {
        const title = userGoal.trim().slice(0, 24) || '新的创作';
        const project = await createProject(title, userGoal);
        const chainRun = await startChainRun(project.projectId, chainType, userGoal);
        this.activeChainType = chainType;
        this.activeChainRun = chainRun;
        this.externalJobs = [];
        this.apiSelectionSnapshots = [];
        this.rememberRun(chainRun);
        return chainRun;
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '链路启动失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async continueConversation(chainType: ChainType, followUp: string) {
      // 连续性对话：没有活动链路时等同新建；有则复用同一项目，
      // 把整段对话作为「记忆」带上（超过上下文阈值时压缩），让模型真正记住前文而非覆盖历史。
      const current = this.activeChainRun;
      if (!current) {
        return this.createAndStart(chainType, followUp);
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const requests = this.recentRuns
          .filter((run) => run.projectId === current.projectId)
          .slice()
          .sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt))
          .map((run) => extractRawRequest(run.userGoal))
          .filter(Boolean);
        // 优先后端 LLM 语义压缩；失败回退本地启发式，保证连续对话始终能进行。
        let memory: string;
        try {
          memory = (await buildConversationMemory(requests)).memory;
        } catch {
          memory = compressConversation(requests, MAX_CONTEXT_CHARS);
        }
        const goal = memory
          ? `【对话记忆(同一角色的连续创作：必须沿用完全一致的人物外貌——性别/年龄/发型发色/五官/身材/服饰款式与配色/标志道具，以及既定风格与场景；仅按本次要求做增量改动，不要更换人物或推翻既定设定)】${memory}\n【本次要求】${followUp.trim()}`
          : followUp.trim();
        const chainRun = await startChainRun(current.projectId, chainType, goal);
        this.activeChainType = chainType;
        this.activeChainRun = chainRun;
        this.externalJobs = [];
        this.apiSelectionSnapshots = [];
        this.rememberRun(chainRun);
        return chainRun;
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '链路启动失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async load(chainRunId: string, silent = false) {
      if (!silent) {
        this.loading = true;
      }
      try {
        const [chainRun, externalJobs, apiSelectionSnapshots] = await Promise.all([
          getChainRun(chainRunId),
          getExternalJobs(chainRunId),
          getApiSelectionSnapshots(chainRunId)
        ]);
        this.activeChainRun = chainRun;
        this.externalJobs = externalJobs;
        this.apiSelectionSnapshots = apiSelectionSnapshots;
        this.rememberRun(chainRun, false);
      } finally {
        if (!silent) {
          this.loading = false;
        }
      }
    },
    isTerminalStatus(status: ChainRunStatus) {
      return TERMINAL_STATUSES.includes(status);
    },
    /**
     * 后端链路异步推进，start/redo 立即返回 EXECUTING。轮询直到到达终态
     * （成功、失败、取消或任一等待态），让 workspace 观测到逐阶段进度。
     */
    async startPolling(chainRunId: string) {
      this.stopPolling();
      await this.load(chainRunId);
      if (this.activeChainRun && this.isTerminalStatus(this.activeChainRun.status)) {
        return;
      }
      this.pollHandle = window.setInterval(() => {
        void this.pollOnce(chainRunId);
      }, POLL_INTERVAL_MS);
    },
    async pollOnce(chainRunId: string) {
      await this.load(chainRunId, true);
      if (this.activeChainRun && this.isTerminalStatus(this.activeChainRun.status)) {
        this.stopPolling();
      }
    },
    stopPolling() {
      if (this.pollHandle !== null) {
        window.clearInterval(this.pollHandle);
        this.pollHandle = null;
      }
    },
    async redoGenerationStage() {
      if (!this.activeChainRun) {
        return null;
      }
      const redoStageRunId = this.stageRunIdForRedo();
      if (!redoStageRunId) {
        this.errorMessage = '当前链路没有可重做的生成阶段';
        throw new Error(this.errorMessage);
      }
      this.loading = true;
      this.errorMessage = '';
      try {
        const redone = await redoStage(redoStageRunId);
        const [externalJobs, apiSelectionSnapshots] = await Promise.all([
          getExternalJobs(redone.chainRunId),
          getApiSelectionSnapshots(redone.chainRunId)
        ]);
        this.activeChainRun = redone;
        this.externalJobs = externalJobs;
        this.apiSelectionSnapshots = apiSelectionSnapshots;
        this.rememberRun(redone);
        return redone;
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '阶段重做失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async cancelActiveChainRun() {
      if (!this.activeChainRun) {
        return null;
      }
      this.stopPolling();
      this.loading = true;
      this.errorMessage = '';
      try {
        const cancelled = await cancelChainRun(this.activeChainRun.chainRunId);
        const [externalJobs, apiSelectionSnapshots] = await Promise.all([
          getExternalJobs(cancelled.chainRunId),
          getApiSelectionSnapshots(cancelled.chainRunId)
        ]);
        this.activeChainRun = cancelled;
        this.externalJobs = externalJobs;
        this.apiSelectionSnapshots = apiSelectionSnapshots;
        this.rememberRun(cancelled);
        return cancelled;
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '链路取消失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    stageRunIdForRedo() {
      if (!this.activeChainRun) {
        return null;
      }
      if (['WAITING_CAPABILITY', 'WAITING_USER', 'WAITING_REVIEW'].includes(this.activeChainRun.status)) {
        for (let index = this.activeChainRun.stageRuns.length - 1; index >= 0; index -= 1) {
          const stage = this.activeChainRun.stageRuns[index];
          if (['WAITING_CAPABILITY', 'WAITING_USER', 'WAITING_REVIEW'].includes(stage.status)) {
            return stage.stageRunId;
          }
        }
      }
      const generationStageCode = this.activeChainRun.chainType === 'IMAGE' ? 'I40' : 'V40';
      const generationStage = this.activeChainRun.stageRuns.find((stage) =>
        stage.stageCode === generationStageCode
      );
      return generationStage?.stageRunId ?? null;
    }
  }
});
