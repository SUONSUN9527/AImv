<template>
  <section class="api-config-panel" aria-label="能力配置">
    <div class="panel-heading">
      <div>
        <h2>{{ title }}</h2>
        <p>{{ description }}</p>
      </div>
      <div v-if="showChainSwitch" class="segmented-control" role="tablist" aria-label="链路选择">
        <button type="button" :class="{ active: activeChain === 'IMAGE' }" @click="setChain('IMAGE')">
          图片链路
        </button>
        <button type="button" :class="{ active: activeChain === 'VIDEO' }" @click="setChain('VIDEO')">
          视频链路
        </button>
      </div>
    </div>
    <p v-if="configStore.errorMessage" class="error-line" role="alert">
      {{ configStore.errorMessage }}
    </p>
    <div class="slot-grid">
      <article v-for="slot in slots" :key="slot.capabilityType" class="slot-card">
        <header>
          <div>
            <h3>{{ slot.label }}</h3>
            <p class="capability-code">
              <span>能力标识</span>
              <code>{{ slot.capabilityType }}</code>
            </p>
          </div>
          <span class="required-dot">必填</span>
        </header>
        <ul class="key-list" v-if="slot.keys.length">
          <li v-for="key in slot.keys" :key="key.apiKeyId">
            <dl class="key-summary" aria-label="Key 配置详情">
              <div>
                <dt>服务商</dt>
                <dd>{{ key.provider }}</dd>
              </div>
              <div>
                <dt>模型名称</dt>
                <dd>{{ key.model || key.label }}</dd>
              </div>
              <div>
                <dt>模型 API Key</dt>
                <dd>{{ key.maskedKey }}</dd>
              </div>
              <div>
                <dt>是否启用</dt>
                <dd :class="{ 'selected-key-badge': key.isSelected }">
                  {{ key.isSelected ? '当前使用中' : '未启用' }}
                </dd>
              </div>
              <div>
                <dt>Key 状态</dt>
                <dd>{{ key.status }}</dd>
              </div>
              <div>
                <dt>免费模型校验</dt>
                <dd>{{ key.freeModelGateStatus }}</dd>
              </div>
              <div>
                <dt>最近校验</dt>
                <dd>{{ key.lastVerifiedAt ?? '未校验' }}</dd>
              </div>
            </dl>
            <div class="key-actions">
              <button type="button" :disabled="configStore.loading" @click="verifyKey(key.apiKeyId)">
                测试
              </button>
              <button type="button" :disabled="configStore.loading" @click="selectKey(key.apiKeyId)">
                设为使用中
              </button>
              <button type="button" :disabled="configStore.loading" @click="removeKey(key.apiKeyId)">
                删除
              </button>
            </div>
          </li>
        </ul>
        <p v-else class="empty-copy">未配置使用中的免费 key</p>
        <button class="add-key-button" type="button" @click="openKeyDialog(slot.capabilityType, slot.label)">
          添加 Key
        </button>
      </article>
    </div>
    <div
      v-if="keyDialog.open"
      class="key-dialog-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="add-key-title"
      @click.self="closeKeyDialog"
      @keydown.esc.prevent="closeKeyDialog"
    >
      <form class="key-dialog" @submit.prevent="submitKey">
        <header>
          <div>
            <h2 id="add-key-title">添加 Key</h2>
            <p>{{ keyDialog.slotLabel }}</p>
          </div>
          <button type="button" aria-label="关闭添加 Key" @click="closeKeyDialog">关闭</button>
        </header>
        <label>
          <span>Provider</span>
          <input ref="providerInput" v-model="keyDialog.provider" aria-label="provider" placeholder="fixture-free" />
        </label>
        <label>
          <span>标签</span>
          <input v-model="keyDialog.label" aria-label="key label" placeholder="标签" />
        </label>
        <label>
          <span>Model</span>
          <input v-model="keyDialog.model" aria-label="model" placeholder="free-model-id" />
        </label>
        <label>
          <span>API Key</span>
          <input
            v-model="keyDialog.apiKey"
            aria-label="api key"
            placeholder="只提交一次明文"
            type="password"
          />
        </label>
        <div class="key-dialog-actions">
          <button type="button" @click="closeKeyDialog">取消</button>
          <button type="submit" :disabled="!canSubmitKey || configStore.loading">提交 Key</button>
        </div>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from 'vue';
import { useApiConfigStore } from '../../stores/useApiConfigStore';
import type { ChainType } from '../../types/api';

const props = withDefaults(defineProps<{
  chainType: ChainType;
  title?: string;
  description?: string;
  showChainSwitch?: boolean;
}>(), {
  showChainSwitch: true
});

const configStore = useApiConfigStore();
const activeChain = ref<ChainType>(props.chainType);
const title = computed(() => props.title ?? '能力配置');
const description = computed(() => props.description ?? '每条链路独立选择免费能力，前端只展示脱敏 key。');
const showChainSwitch = computed(() => props.showChainSwitch);
const providerInput = ref<HTMLInputElement | null>(null);
const keyDialog = reactive({
  open: false,
  capabilityType: '',
  slotLabel: '',
  provider: '',
  label: '',
  model: '',
  apiKey: ''
});
const slots = computed(() => configStore.slotsByChain[activeChain.value]);
const canSubmitKey = computed(() => Boolean(
  keyDialog.capabilityType && keyDialog.provider.trim() && keyDialog.label.trim() && keyDialog.apiKey.trim()
));

async function setChain(chainType: ChainType) {
  activeChain.value = chainType;
  closeKeyDialog();
  await configStore.load(chainType);
}

function openKeyDialog(capabilityType: string, slotLabel: string) {
  keyDialog.open = true;
  keyDialog.capabilityType = capabilityType;
  keyDialog.slotLabel = slotLabel;
  keyDialog.provider = '';
  keyDialog.label = '';
  keyDialog.model = '';
  keyDialog.apiKey = '';
  void nextTick(() => providerInput.value?.focus());
}

function closeKeyDialog() {
  keyDialog.open = false;
  keyDialog.capabilityType = '';
  keyDialog.slotLabel = '';
  keyDialog.provider = '';
  keyDialog.label = '';
  keyDialog.model = '';
  keyDialog.apiKey = '';
}

async function submitKey() {
  if (!canSubmitKey.value) {
    return;
  }
  const input = {
    chainType: activeChain.value,
    capabilityType: keyDialog.capabilityType,
    provider: keyDialog.provider.trim(),
    label: keyDialog.label.trim(),
    apiKey: keyDialog.apiKey,
    model: keyDialog.model.trim() || undefined
  };
  keyDialog.apiKey = '';
  try {
    await configStore.addKey(input);
    closeKeyDialog();
  } catch {
    // Store owns the user-facing error message; keep the dialog open for correction.
  } finally {
    input.apiKey = '';
  }
}

async function verifyKey(apiKeyId: string) {
  try {
    await configStore.verify(activeChain.value, apiKeyId);
  } catch {
    // Store owns the user-facing error message.
  }
}

async function selectKey(apiKeyId: string) {
  try {
    await configStore.select(activeChain.value, apiKeyId);
  } catch {
    // Store owns the user-facing error message.
  }
}

async function removeKey(apiKeyId: string) {
  try {
    await configStore.remove(activeChain.value, apiKeyId);
  } catch {
    // Store owns the user-facing error message.
  }
}
</script>
