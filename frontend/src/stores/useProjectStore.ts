import { defineStore } from 'pinia';
import { listProjects } from '../api/projects';
import type { Project } from '../types/api';

export const useProjectStore = defineStore('projects', {
  state: () => ({
    projects: [] as Project[],
    currentProject: null as Project | null,
    loading: false,
    errorMessage: ''
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
    }
  },
  actions: {
    async loadRecent() {
      this.loading = true;
      this.errorMessage = '';
      try {
        this.projects = await listProjects();
      } catch (error) {
        this.errorMessage = error instanceof Error ? error.message : '项目历史读取失败';
      } finally {
        this.loading = false;
      }
    }
  }
});
