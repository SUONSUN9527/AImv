import { defineStore } from 'pinia';
import { deleteProject, listProjects, setProjectPinned } from '../api/projects';
import type { Project } from '../types/api';

export const useProjectStore = defineStore('projects', {
  state: () => ({
    projects: [] as Project[],
    currentProject: null as Project | null,
    loading: false,
    errorMessage: '',
    // 置顶的 projectId，顺序即置顶优先级（最近置顶在前）。
    // 从服务端 pinnedAt 播种，置顶操作时乐观更新以便立即重排。
    pinnedProjectIds: [] as string[]
  }),
  getters: {
    uniqueRecentProjects: (state): Project[] => {
      const seen = new Set<string>();
      return state.projects.filter((project) => {
        const key = project.title.trim() || project.goal.trim();
        if (!key || seen.has(key)) {
          return false;
        }
        seen.add(key);
        return true;
      });
    },
    isPinned: (state) => (projectId: string) => state.pinnedProjectIds.includes(projectId)
  },
  actions: {
    async loadRecent() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.projects = await listProjects();
        // 以服务端 pinnedAt 为权威重新播种置顶顺序（按置顶时间倒序）。
        this.pinnedProjectIds = this.projects
          .filter((project) => project.pinnedAt)
          .slice()
          .sort((left, right) => Date.parse(right.pinnedAt ?? '') - Date.parse(left.pinnedAt ?? ''))
          .map((project) => project.projectId);
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '项目历史读取失败';
      } finally {
        this.loading = false;
      }
    },
    // 删除一段对话：后端级联删除，本地列表与置顶集合同步移除（不再依赖 localStorage 隐藏）。
    async remove(projectId: string) {
      this.projects = this.projects.filter((project) => project.projectId !== projectId);
      this.pinnedProjectIds = this.pinnedProjectIds.filter((id) => id !== projectId);
      await deleteProject(projectId);
    },
    // 置顶/取消置顶：先乐观更新顺序立即重排，再持久化到后端；下次 loadRecent 以服务端为准。
    async setPinned(projectId: string, pinned: boolean) {
      this.pinnedProjectIds = pinned
        ? [projectId, ...this.pinnedProjectIds.filter((id) => id !== projectId)]
        : this.pinnedProjectIds.filter((id) => id !== projectId);
      const project = this.projects.find((item) => item.projectId === projectId);
      if (project) {
        project.pinnedAt = pinned ? new Date().toISOString() : null;
      }
      await setProjectPinned(projectId, pinned);
    }
  }
});
