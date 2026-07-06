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
      <button type="button" role="menuitem" @click="pinHistoryItem(historyMenu.itemId)">
        {{ isHistoryItemPinned(historyMenu.itemId) ? '取消置顶' : '置顶' }}
      </button>
      <button type="button" role="menuitem" @click="deleteHistoryItem(historyMenu.itemId)">删除</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { PanelLeftCloseIcon, PencilIcon } from '@lucide/vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { useChainRunStore } from '../../stores/useChainRunStore';
import { useProjectStore } from '../../stores/useProjectStore';
import { useShellStore } from '../../stores/useShellStore';
import { buildSidebarHistoryItems, type SidebarHistoryItem } from './historyItems';

const HISTORY_TITLE_STORAGE_KEY = 'aimv.historyTitleOverrides';
const HISTORY_HIDDEN_STORAGE_KEY = 'aimv.historyHiddenIds';
const HISTORY_READ_COMPLETED_STORAGE_KEY = 'aimv.historyReadCompletedIds';
const shell = useShellStore();
const route = useRoute();
const router = useRouter();
const chainRuns = useChainRunStore();
const projects = useProjectStore();
const titleOverrides = ref(readTitleOverrides());
// 置顶改为后端持久化：置顶集合由 projects store 维护（服务端 pinnedAt 播种 + 乐观更新），不再依赖 localStorage。
const pinnedIds = computed(() => projects.pinnedProjectIds);
const hiddenHistoryIds = ref(readStringList(HISTORY_HIDDEN_STORAGE_KEY));
const readCompletedHistoryIds = ref(readStringList(HISTORY_READ_COMPLETED_STORAGE_KEY));
const editingHistoryId = ref('');
const renameDraft = ref('');
const renameInputRef = ref<HTMLInputElement | null>(null);
const historyMenu = ref<{ itemId: string; x: number; y: number } | null>(null);
let resizeState: { pointerId: number; startX: number; startWidth: number; target: HTMLElement } | null = null;

const historyItems = computed(() =>
  buildSidebarHistoryItems(chainRuns.recentRuns, projects.uniqueRecentProjects, titleOverrides.value, {
    pinnedIds: pinnedIds.value,
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
  return item.chainRunId !== currentChainRunId.value && !readCompletedHistoryIds.value.includes(item.id);
}

function historyAriaLabel(item: SidebarHistoryItem) {
  return shouldShowHistoryStatus(item) ? `${item.title}，${item.statusAriaLabel}` : item.title;
}

function markCurrentCompletedHistoryAsRead() {
  const item = historyItems.value.find((historyItem) => historyItem.chainRunId === currentChainRunId.value);
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

function isHistoryItemPinned(itemId: string) {
  return projects.isPinned(itemId);
}

// 置顶/取消置顶：后端持久化（按 projectId），失败静默不打断用户。
async function pinHistoryItem(itemId: string) {
  const pinned = isHistoryItemPinned(itemId);
  closeHistoryMenu();
  try {
    await projects.setPinned(itemId, !pinned);
  } catch {
    // 网络失败时保持原状，不向用户抛错。
  }
}

// 删除对话：后端级联删除 + 清本地链路缓存；当前打开的正是该会话时退回新对话页。
async function deleteHistoryItem(itemId: string) {
  closeHistoryMenu();
  const wasActive = chainRuns.activeChainRun?.projectId === itemId;
  chainRuns.forgetProjectRuns(itemId);
  // 兜底：先本地隐藏，避免删除请求往返期间列表闪回。
  const nextHiddenIds = Array.from(new Set([...hiddenHistoryIds.value, itemId]));
  hiddenHistoryIds.value = nextHiddenIds;
  persistStringList(HISTORY_HIDDEN_STORAGE_KEY, nextHiddenIds);
  readCompletedHistoryIds.value = readCompletedHistoryIds.value.filter((id) => id !== itemId);
  persistStringList(HISTORY_READ_COMPLETED_STORAGE_KEY, readCompletedHistoryIds.value);
  const nextOverrides = { ...titleOverrides.value };
  delete nextOverrides[itemId];
  titleOverrides.value = nextOverrides;
  persistTitleOverrides(nextOverrides);
  try {
    await projects.remove(itemId);
  } catch {
    // 后端删除失败也已本地隐藏；下次刷新会重试可见性。
  }
  if (wasActive) {
    void router.push('/generate');
  }
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

// 侧边栏常驻，切换对话时不会重挂载；用轮询刷新服务端状态，
// 保证「切出去还在跑」的对话在其后台完成后能自动从「生成中」变为完成，无需手动点进去。
const STATUS_REFRESH_MS = 4000;
let statusRefreshTimer: number | null = null;
function hasRunningHistory() {
  return historyItems.value.some((item) => item.statusState === 'running');
}
onBeforeUnmount(() => {
  stopResize();
  closeHistoryMenu();
  if (statusRefreshTimer !== null) {
    window.clearInterval(statusRefreshTimer);
    statusRefreshTimer = null;
  }
});
watch([historyItems, currentChainRunId], markCurrentCompletedHistoryAsRead, { immediate: true });
// 切换/打开对话时刷新一次状态，让刚完成的对话尽快脱离转圈。
watch(currentChainRunId, () => {
  void projects.loadRecent();
});
onMounted(() => {
  void projects.loadRecent();
  statusRefreshTimer = window.setInterval(() => {
    if (hasRunningHistory()) {
      void projects.loadRecent();
    }
  }, STATUS_REFRESH_MS);
});
</script>
