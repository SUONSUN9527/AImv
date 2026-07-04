import { defineStore } from 'pinia';

export const useShellStore = defineStore('shell', {
  state: () => ({
    sidebarCollapsed: false,
    sidebarWidth: 200
  }),
  actions: {
    collapseSidebar() {
      this.sidebarCollapsed = true;
    },
    expandSidebar() {
      this.sidebarCollapsed = false;
    },
    setSidebarWidth(width: number) {
      this.sidebarWidth = Math.min(280, Math.max(180, width));
    }
  }
});
