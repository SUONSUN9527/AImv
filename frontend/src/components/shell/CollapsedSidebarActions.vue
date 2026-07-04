<template>
  <div class="collapsed-sidebar-actions">
    <button class="icon-button" type="button" aria-label="展开对话" @click="shell.expandSidebar">
      <PanelLeftOpenIcon :size="18" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { PanelLeftOpenIcon } from '@lucide/vue';
import { useShellStore } from '../../stores/useShellStore';

const shell = useShellStore();
</script>
