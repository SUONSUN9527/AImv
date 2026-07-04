<template>
  <section class="capability-page">
    <header class="capability-hero">
      <p>能力</p>
      <h1>能力配置</h1>
      <span>图片链路和视频链路分开配置，每个能力项单独占据一行。</span>
    </header>
    <div class="capability-stack">
      <ApiConfigPanel
        chain-type="IMAGE"
        title="链路能力配置"
        description="选择图片或视频链路后，仅展示该链路需要的免费能力配置。"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import ApiConfigPanel from '../components/capability/ApiConfigPanel.vue';
import { useApiConfigStore } from '../stores/useApiConfigStore';

const configStore = useApiConfigStore();

onMounted(async () => {
  try {
    await configStore.load('IMAGE');
  } catch {
    // ApiConfigPanel renders the store-owned user-facing error.
  }
});
</script>
