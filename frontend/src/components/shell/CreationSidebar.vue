<template>
  <aside class="creation-sidebar" :style="{ width: `${shell.sidebarWidth}px` }">
    <div class="sidebar-header">
      <h2>对话</h2>
      <button class="icon-button" type="button" aria-label="折叠对话" @click="shell.collapseSidebar">
        <PanelLeftCloseIcon :size="18" />
      </button>
    </div>
    <RouterLink class="new-chat-button" to="/generate">新对话</RouterLink>
    <section class="history-content" aria-label="对话">
      <div v-if="historyItems.length > 0" class="history-list" role="list">
        <div
          v-for="item in historyItems"
          :key="item.id"
          class="history-item"
          :class="{ 'history-item-disabled': !item.to, 'history-item-editing': editingHistoryId === item.id }"
          role="listitem"
          @contextmenu.prevent="openHistoryMenu($event, item)"
        >
          <input
            v-if="editingHistoryId === item.id"
            :ref="setRenameInputRef"
            v-model="renameDraft"
            class="history-rename-input"
            aria-label="对话名称"
            @keydown.enter.prevent="commitRename(item.id)"
            @keydown.esc.prevent="cancelRename"
            @blur="commitRename(item.id)"
          />
          <RouterLink
            v-else-if="item.to"
            class="history-main"
            :to="item.to"
            :aria-label="historyAriaLabel(item)"
          >
            <span class="history-title">{{ item.title }}</span>
          </RouterLink>
          <div v-else class="history-main" aria-disabled="true" :title="item.hint">
            <span class="history-title">{{ item.title }}</span>
          </div>
          <span
            v-if="shouldShowHistoryStatus(item)"
            class="history-status"
            :data-state="item.statusState"
            :aria-label="item.statusAriaLabel"
            role="status"
          >
            <span class="history-status-mark" aria-hidden="true"></span>
          </span>
          <button
            v-if="editingHistoryId !== item.id"
            class="history-rename-button"
            type="button"
            aria-label="修改对话名称"
            title="修改对话名称"
            @click="startRename(item)"
          >
            <PencilIcon :size="14" />
          </button>
        </div>
      </div>
      <p v-if="historyItems.length === 0" class="empty-copy">暂无对话</p>
    </section>
    <div
      class="sidebar-resizer"
      role="separator"
      aria-label="调整侧边栏宽度"
      aria-valuemin="180"
      aria-valuemax="280"
      :aria-valuenow="shell.sidebarWidth"
      tabindex="0"
      @pointerdown="onResizePointerDown"
      @keydown="onResizeKeydown"
    />
    <div
      v-if="historyMenu"
      class="history-context-menu"
      role="menu"
      :style="{ left: `${historyMenu.x}px`, top: `${historyMenu.y}px` }"
      @contextmenu.prevent
    >
      <button type="button" role="menuitem" @click="pinHistoryItem(historyMenu.itemId)">置顶</button>
      <button type="button" role="menuitem" @click="deleteHistoryItem(historyMenu.itemId)">删除</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { PanelLeftCloseIcon, PencilIcon } from '@lucide/vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { useChainRunStore } from '../../stores/useChainRunStore';
import { useProjectStore } from '../../stores/useProjectStore';
import { useShellStore } from '../../stores/useShellStore';
import { buildSidebarHistoryItems, type SidebarHistoryItem } from './historyItems';

const HISTORY_TITLE_STORAGE_KEY = 'aimv.historyTitleOverrides';
const HISTORY_PIN_STORAGE_KEY = 'aimv.historyPinnedIds';
const HISTORY_HIDDEN_STORAGE_KEY = 'aimv.historyHiddenIds';
const HISTORY_READ_COMPLETED_STORAGE_KEY = 'aimv.historyReadCompletedIds';
const shell = useShellStore();
const route = useRoute();
const chainRuns = useChainRunStore();
const projects = useProjectStore();
const titleOverrides = ref(readTitleOverrides());
const pinnedHistoryIds = ref(readStringList(HISTORY_PIN_STORAGE_KEY));
const hiddenHistoryIds = ref(readStringList(HISTORY_HIDDEN_STORAGE_KEY));
const readCompletedHistoryIds = ref(readStringList(HISTORY_READ_COMPLETED_STORAGE_KEY));
const editingHistoryId = ref('');
const renameDraft = ref('');
const renameInputRef = ref<HTMLInputElement | null>(null);
const historyMenu = ref<{ itemId: string; x: number; y: number } | null>(null);
let resizeState: { pointerId: number; startX: number; startWidth: number; target: HTMLElement } | null = null;

const historyItems = computed(() =>
  buildSidebarHistoryItems(chainRuns.recentRuns, projects.uniqueRecentProjects, titleOverrides.value, {
    pinnedIds: pinnedHistoryIds.value,
    hiddenIds: hiddenHistoryIds.value
  })
);
const currentChainRunId = computed(() => {
  const chainRunId = route.params.chainRunId;
  return typeof chainRunId === 'string' ? chainRunId : '';
});

function shouldShowHistoryStatus(item: SidebarHistoryItem) {
  if (item.statusState === 'running') {
    return true;
  }
  if (item.statusState !== 'complete') {
    return false;
  }
  return item.id !== currentChainRunId.value && !readCompletedHistoryIds.value.includes(item.id);
}

function historyAriaLabel(item: SidebarHistoryItem) {
  return shouldShowHistoryStatus(item) ? `${item.title}，${item.statusAriaLabel}` : item.title;
}

function markCurrentCompletedHistoryAsRead() {
  const item = historyItems.value.find((historyItem) => historyItem.id === currentChainRunId.value);
  if (!item || item.statusState !== 'complete' || readCompletedHistoryIds.value.includes(item.id)) {
    return;
  }
  readCompletedHistoryIds.value = [...readCompletedHistoryIds.value, item.id];
  persistStringList(HISTORY_READ_COMPLETED_STORAGE_KEY, readCompletedHistoryIds.value);
}

function onResizePointerDown(event: PointerEvent) {
  if (event.button !== 0) {
    return;
  }
  resizeState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startWidth: shell.sidebarWidth,
    target: event.currentTarget as HTMLElement
  };
  resizeState.target.setPointerCapture?.(event.pointerId);
  window.addEventListener('pointermove', onResizePointerMove);
  window.addEventListener('pointerup', onResizePointerUp);
  event.preventDefault();
}

function onResizePointerMove(event: PointerEvent) {
  if (!resizeState || event.pointerId !== resizeState.pointerId) {
    return;
  }
  shell.setSidebarWidth(resizeState.startWidth + event.clientX - resizeState.startX);
}

function onResizePointerUp(event: PointerEvent) {
  if (!resizeState || event.pointerId !== resizeState.pointerId) {
    return;
  }
  resizeState.target.releasePointerCapture?.(event.pointerId);
  stopResize();
}

function stopResize() {
  window.removeEventListener('pointermove', onResizePointerMove);
  window.removeEventListener('pointerup', onResizePointerUp);
  resizeState = null;
}

function onResizeKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowLeft') {
    shell.setSidebarWidth(shell.sidebarWidth - 12);
  }
  if (event.key === 'ArrowRight') {
    shell.setSidebarWidth(shell.sidebarWidth + 12);
  }
  if (event.key === 'Home') {
    shell.setSidebarWidth(180);
  }
  if (event.key === 'End') {
    shell.setSidebarWidth(280);
  }
}

function startRename(item: SidebarHistoryItem) {
  closeHistoryMenu();
  editingHistoryId.value = item.id;
  renameDraft.value = item.title;
  void nextTick(() => {
    renameInputRef.value?.focus();
    renameInputRef.value?.select();
  });
}

function commitRename(itemId: string) {
  if (editingHistoryId.value !== itemId) {
    return;
  }
  const nextOverrides = { ...titleOverrides.value };
  const title = renameDraft.value.trim();
  if (title) {
    nextOverrides[itemId] = title;
  } else {
    delete nextOverrides[itemId];
  }
  titleOverrides.value = nextOverrides;
  persistTitleOverrides(nextOverrides);
  editingHistoryId.value = '';
  renameDraft.value = '';
}

function cancelRename() {
  editingHistoryId.value = '';
  renameDraft.value = '';
}

function setRenameInputRef(element: Element | null) {
  renameInputRef.value = element instanceof HTMLInputElement ? element : null;
}

function openHistoryMenu(event: MouseEvent, item: SidebarHistoryItem) {
  closeHistoryMenu();
  const menuWidth = 136;
  const menuHeight = 84;
  historyMenu.value = {
    itemId: item.id,
    x: Math.min(event.clientX, window.innerWidth - menuWidth - 8),
    y: Math.min(event.clientY, window.innerHeight - menuHeight - 8)
  };
  window.addEventListener('click', closeHistoryMenu, { once: true });
  window.addEventListener('keydown', closeHistoryMenuOnEscape);
  window.addEventListener('scroll', closeHistoryMenu, { once: true, capture: true });
}

function closeHistoryMenu() {
  historyMenu.value = null;
  window.removeEventListener('click', closeHistoryMenu);
  window.removeEventListener('scroll', closeHistoryMenu, true);
  window.removeEventListener('keydown', closeHistoryMenuOnEscape);
}

function closeHistoryMenuOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeHistoryMenu();
  }
}

function pinHistoryItem(itemId: string) {
  const nextIds = [itemId, ...pinnedHistoryIds.value.filter((id) => id !== itemId)];
  pinnedHistoryIds.value = nextIds;
  persistStringList(HISTORY_PIN_STORAGE_KEY, nextIds);
  closeHistoryMenu();
}

function deleteHistoryItem(itemId: string) {
  const nextHiddenIds = Array.from(new Set([...hiddenHistoryIds.value, itemId]));
  hiddenHistoryIds.value = nextHiddenIds;
  pinnedHistoryIds.value = pinnedHistoryIds.value.filter((id) => id !== itemId);
  readCompletedHistoryIds.value = readCompletedHistoryIds.value.filter((id) => id !== itemId);
  const nextOverrides = { ...titleOverrides.value };
  delete nextOverrides[itemId];
  titleOverrides.value = nextOverrides;
  persistStringList(HISTORY_HIDDEN_STORAGE_KEY, nextHiddenIds);
  persistStringList(HISTORY_PIN_STORAGE_KEY, pinnedHistoryIds.value);
  persistStringList(HISTORY_READ_COMPLETED_STORAGE_KEY, readCompletedHistoryIds.value);
  persistTitleOverrides(nextOverrides);
  closeHistoryMenu();
}

function readTitleOverrides() {
  const storage = browserStorage();
  if (!storage) {
    return {};
  }
  try {
    const parsed: unknown = JSON.parse(storage.getItem(HISTORY_TITLE_STORAGE_KEY) ?? '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {};
    }
    return Object.fromEntries(
      Object.entries(parsed).filter((entry): entry is [string, string] => typeof entry[1] === 'string')
    );
  } catch {
    return {};
  }
}

function readStringList(storageKey: string) {
  const storage = browserStorage();
  if (!storage) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(storage.getItem(storageKey) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

function persistStringList(storageKey: string, values: string[]) {
  const storage = browserStorage();
  if (!storage) {
    return;
  }
  try {
    storage.setItem(storageKey, JSON.stringify(values));
  } catch {
    // 本地存储不可写时仅保留当前会话状态。
  }
}

function persistTitleOverrides(overrides: Record<string, string>) {
  const storage = browserStorage();
  if (!storage) {
    return;
  }
  try {
    storage.setItem(HISTORY_TITLE_STORAGE_KEY, JSON.stringify(overrides));
  } catch {
    // 本地存储不可写时保留当前会话里的重命名结果。
  }
}

function browserStorage() {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}

onBeforeUnmount(() => {
  stopResize();
  closeHistoryMenu();
});
watch([historyItems, currentChainRunId], markCurrentCompletedHistoryAsRead, { immediate: true });
onMounted(() => {
  void projects.loadRecent();
});
</script>
