<template>
  <section class="generate-page">
    <div class="generate-stage">
      <div class="generate-center">
        <h1>开启你的movie生涯！</h1>
        <PromptComposer
          v-model="prompt"
          v-model:chain-type="chainType"
          :submitting="chainRuns.loading"
          @submit="handleSubmit"
        />
        <p v-if="message" class="error-line">{{ message }}</p>
      </div>

      <div class="creation-mode-strip" aria-label="生成入口">
        <button
          v-for="card in creationCards"
          :key="card.title"
          type="button"
          :class="{ active: card.chainType === chainType }"
          @click="chainType = card.chainType"
        >
          <span class="mode-thumb" :class="card.tone">{{ card.badge }}</span>
          <span>
            <strong>{{ card.title }}</strong>
            <small>{{ card.subtitle }}</small>
          </span>
        </button>
      </div>
    </div>

    <section class="discovery-panel" aria-label="推荐作品">
      <div class="discovery-grid">
        <article
          v-for="item in discoveryItems"
          :key="item.title"
          class="discovery-card"
          :class="item.size"
        >
          <img :src="item.image" :alt="item.title" />
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.meta }}</span>
          </div>
        </article>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ApiClientError } from '../api/http';
import PromptComposer from '../components/composer/PromptComposer.vue';
import { creationCards, discoveryItems } from '../constants/generateCatalog';
import { useApiConfigStore } from '../stores/useApiConfigStore';
import { useChainRunStore } from '../stores/useChainRunStore';
import type { ChainType } from '../types/api';

const router = useRouter();
const configStore = useApiConfigStore();
const chainRuns = useChainRunStore();
const prompt = ref('');
const chainType = ref<ChainType>('IMAGE');
const message = ref('');
const capabilityErrorCodes = new Set([
  'API_CAPABILITY_NOT_CONFIGURED',
  'FREE_MODEL_GATE_FAILED',
  'FREE_QUOTA_EXHAUSTED'
]);

async function handleSubmit(value: string) {
  message.value = '';
  await configStore.load(chainType.value);
  if (configStore.hasMissingSelected(chainType.value)) {
    message.value = '缺少使用中的免费能力配置，请先完成链路级 API key 选择。';
    await router.push('/capability');
    return;
  }
  try {
    const chainRun = await chainRuns.createAndStart(chainType.value, value);
    await router.push(`/workspace/${chainRun.chainRunId}`);
  } catch (error) {
    message.value = error instanceof Error ? error.message : '链路启动失败';
    if (error instanceof ApiClientError && capabilityErrorCodes.has(error.code)) {
      await router.push('/capability');
    }
  }
}
</script>
