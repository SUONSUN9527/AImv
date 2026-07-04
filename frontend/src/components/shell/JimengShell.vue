<template>
  <div class="jimeng-shell" :class="shellClass" :style="shellStyle">
    <RailNav />
    <CreationSidebar v-if="showHistorySidebar && !shell.sidebarCollapsed" />
    <CollapsedSidebarActions v-if="showHistorySidebar && shell.sidebarCollapsed" />
    <main class="jimeng-main">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useShellStore } from '../../stores/useShellStore';
import CollapsedSidebarActions from './CollapsedSidebarActions.vue';
import CreationSidebar from './CreationSidebar.vue';
import RailNav from './RailNav.vue';

const route = useRoute();
const shell = useShellStore();
const showHistorySidebar = computed(() => route.path.startsWith('/generate') || route.path.startsWith('/workspace'));
const shellStyle = computed(() => ({
  '--history-sidebar-width': `${shell.sidebarWidth}px`
}));
const shellClass = computed(() => ({
  'with-history': showHistorySidebar.value && !shell.sidebarCollapsed,
  'history-collapsed': showHistorySidebar.value && shell.sidebarCollapsed,
  'asset-view': route.path.startsWith('/assets'),
  'capability-view': route.path.startsWith('/capability'),
  'workspace-view': route.path.startsWith('/workspace')
}));
</script>
