<template>
  <section class="workspace-page">
    <div class="workspace-feed">
      <header class="workspace-header">
        <h1>今天</h1>
        <div v-if="chainRun && !isSucceeded" class="workspace-actions">
          <button type="button" :disabled="chainRuns.loading" @click="redoGeneration">
            <RefreshCwIcon :size="14" />
            <span>再次生成</span>
          </button>
          <button
            v-if="canCancelChain"
            type="button"
            :disabled="chainRuns.loading"
            @click="cancelChain"
          >
            <XCircleIcon :size="14" />
            <span>取消链路</span>
          </button>
        </div>
      </header>
      <div v-if="chainRun" class="conversation-thread">
        <!-- 历史轮次（同一对话内的既往生成）：只读展示，滚动查看整段连续对话 -->
        <article v-for="turn in previousTurns" :key="turn.chainRunId" class="conversation-turn">
          <div class="user-bubble">{{ extractRawRequest(turn.userGoal) }}</div>
          <div class="assistant-block">
            <ResultStrip
              v-if="turnSucceeded(turn)"
              :artifacts="turn.artifacts"
              :chain-type="turn.chainType"
            />
            <p v-else class="turn-note">{{ turnFailed(turn) ? '生成失败' : '生成中…' }}</p>
          </div>
        </article>
        <!-- 最新轮次：可编辑目标、看状态、再次生成 -->
        <article class="conversation-turn">
        <form v-if="isEditingGoal" class="user-edit-form" @submit.prevent="confirmEdit">
          <label class="sr-only" for="workspace-goal-editor">编辑创作目标</label>
          <textarea
            id="workspace-goal-editor"
            ref="editorRef"
            v-model="editDraft"
            aria-label="编辑创作目标"
            rows="4"
            @keydown.esc.prevent="cancelEdit"
          />
          <div class="user-edit-actions">
            <button type="button" @click="cancelEdit">取消</button>
            <button type="submit" :disabled="!editDraft.trim() || chainRuns.loading">确定</button>
          </div>
        </form>
        <div v-else class="user-bubble">{{ extractRawRequest(chainRun.userGoal) }}</div>
        <div class="assistant-block">
          <ResultStrip
            v-if="isSucceeded"
            :artifacts="chainRun.artifacts"
            :chain-type="chainRun.chainType"
          />
          <section v-if="isSucceeded" class="message-action-row" aria-label="结果操作">
            <button type="button" :disabled="chainRuns.loading" @click="redoGeneration">
              <RefreshCwIcon :size="14" />
              <span>再生成</span>
            </button>
            <button type="button" @click="startInlineEdit">
              <PencilIcon :size="14" />
              <span>调整目标</span>
            </button>
          </section>
          <template v-else>
            <p>{{ statusCopy }}</p>
            <div v-if="shouldShowCapabilityAction" class="status-actions">
              <button type="button" :disabled="configStore.loading" @click="openCapabilityConfig">去配置</button>
            </div>
          </template>
        </div>
        </article>
      </div>
      <div v-else-if="chainRuns.loading" class="workspace-loading-skeleton" aria-label="生成加载骨架">
        <div class="loading-skeleton-media">
          <img src="/history-source.png" alt="生成加载素材" />
        </div>
        <div class="loading-skeleton-lines" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>
      </div>
      <div v-else class="empty-workspace">选择一次生成记录后查看链路状态。</div>
    </div>
    <div class="bottom-composer">
      <PromptComposer v-model="draft" v-model:chain-type="chainType" @submit="submitPrompt" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { PencilIcon, RefreshCwIcon, XCircleIcon } from '@lucide/vue';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PromptComposer from '../components/composer/PromptComposer.vue';
import ResultStrip from '../components/workspace/ResultStrip.vue';
import { useApiConfigStore } from '../stores/useApiConfigStore';
import { useChainRunStore } from '../stores/useChainRunStore';
import type { ChainType } from '../types/api';
import { extractRawRequest } from '../utils/conversation';

// 失败原因清洗：隐藏 SQL/堆栈等技术细节，只放行简短可读的原因给用户。
function cleanFailReason(reason?: string | null): string {
  if (!reason) {
    return '';
  }
  if (/SQL|Exception|INSERT|PreparedStatement|\bat [a-zA-Z]+\./.test(reason)) {
    return '';
  }
  const trimmed = reason.trim();
  return trimmed.length > 80 ? `${trimmed.slice(0, 80)}…` : trimmed;
}

const route = useRoute();
const router = useRouter();
const configStore = useApiConfigStore();
const chainRuns = useChainRunStore();
const draft = ref('');
const chainType = ref<ChainType>('IMAGE');
const editDraft = ref('');
const isEditingGoal = ref(false);
const editorRef = ref<HTMLTextAreaElement | null>(null);
const chainRun = computed(() => chainRuns.activeChainRun);
const isSucceeded = computed(() => chainRun.value?.status === 'SUCCEEDED');
// 同一对话 = 同一项目下的所有链路，按时间升序即为多轮消息流。
const conversationTurns = computed(() => {
  const active = chainRun.value;
  if (!active) {
    return [];
  }
  return chainRuns.recentRuns
    .filter((run) => run.projectId === active.projectId)
    .slice()
    .sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt));
});
// 除最新轮次外的既往轮次（最新轮次由下方带操作的 article 单独渲染）。
const previousTurns = computed(() =>
  conversationTurns.value.filter((run) => run.chainRunId !== chainRun.value?.chainRunId)
);
function turnSucceeded(run: { status: string }) {
  return run.status === 'SUCCEEDED';
}
function turnFailed(run: { status: string }) {
  return ['FAILED', 'CANCELLED'].includes(run.status);
}
const shouldShowCapabilityAction = computed(() => chainRun.value?.status === 'WAITING_CAPABILITY');
const canCancelChain = computed(() => Boolean(
  chainRun.value
    && !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(chainRun.value.status)
));
const statusCopy = computed(() => {
  if (!chainRun.value) {
    return '';
  }
  if (chainRun.value.status === 'SUCCEEDED') {
    return '链路已完成，最终产物和验收报告已固化。';
  }
  if (['WAITING_CAPABILITY', 'WAITING_USER', 'WAITING_REVIEW'].includes(chainRun.value.status)) {
    return chainRun.value.blockingReason ?? '等待处理后继续链路。';
  }
  if (chainRun.value.status === 'QUALITY_REVIEWING') {
    return '正在验收结果';
  }
  if (chainRun.value.status === 'HANDOFF_WRITING' || chainRun.value.status === 'KNOWLEDGE_INGESTING') {
    return '正在整理交付信息';
  }
  if (chainRun.value.status === 'FAILED') {
    // 展示可读的失败原因（隐藏 SQL/堆栈等技术细节）。
    const reason = cleanFailReason(chainRun.value.blockingReason);
    return reason ? `生成失败：${reason}` : '生成失败，请点击"再次生成"重试';
  }
  if (chainRun.value.status === 'CANCELLED') {
    return '链路已取消，可重新生成。';
  }
  return '正在生成中。';
});

onMounted(() => {
  const chainRunId = route.params.chainRunId;
  if (typeof chainRunId === 'string') {
    void chainRuns.startPolling(chainRunId);
  }
});

onUnmounted(() => {
  chainRuns.stopPolling();
});

watch(
  () => route.params.chainRunId,
  (value) => {
    if (typeof value === 'string' && value) {
      void chainRuns.startPolling(value);
    }
  }
);

watch(chainRun, (value) => {
  if (!value) {
    return;
  }
  chainType.value = value.chainType;
  if (!isEditingGoal.value) {
    editDraft.value = extractRawRequest(value.userGoal);
  }
}, { immediate: true });

function startInlineEdit() {
  if (!chainRun.value) {
    return;
  }
  editDraft.value = extractRawRequest(chainRun.value.userGoal);
  isEditingGoal.value = true;
  void nextTick(() => editorRef.value?.focus());
}

function cancelEdit() {
  isEditingGoal.value = false;
  editDraft.value = chainRun.value ? extractRawRequest(chainRun.value.userGoal) : '';
}

async function confirmEdit() {
  const value = editDraft.value.trim();
  if (!value) {
    return;
  }
  isEditingGoal.value = false;
  await startAgain(value);
}

async function redoGeneration() {
  if (chainRun.value) {
    const redone = await chainRuns.redoGenerationStage();
    if (redone) {
      void chainRuns.startPolling(redone.chainRunId);
    }
  }
}

async function cancelChain() {
  if (chainRun.value) {
    await chainRuns.cancelActiveChainRun();
  }
}

async function openCapabilityConfig() {
  if (!chainRun.value) {
    return;
  }
  await router.push('/capability');
}

async function startAgain(value: string) {
  await configStore.load(chainType.value);
  if (configStore.hasMissingSelected(chainType.value)) {
    await router.push('/capability');
    return;
  }
  const created = await chainRuns.createAndStart(chainType.value, value);
  await router.push(`/workspace/${created.chainRunId}`);
}

// 底部输入框：有活动对话时延续上下文（连续性对话，不覆盖历史），否则新建。
async function submitPrompt(value: string) {
  await configStore.load(chainType.value);
  if (configStore.hasMissingSelected(chainType.value)) {
    await router.push('/capability');
    return;
  }
  const created = chainRun.value
    ? await chainRuns.continueConversation(chainType.value, value)
    : await chainRuns.createAndStart(chainType.value, value);
  await router.push(`/workspace/${created.chainRunId}`);
}
</script>
