<template>
  <nav class="jimeng-rail" aria-label="主导航">
    <RouterLink class="spark-logo" to="/generate" aria-label="AImv 首页">
      <SparklesIcon :size="22" />
    </RouterLink>
    <RouterLink class="rail-item" :class="{ active: isGenerate }" to="/generate">
      <ImageIcon :size="19" />
      <span>生成</span>
    </RouterLink>
    <RouterLink class="rail-item" :class="{ active: isAssets }" to="/assets">
      <FolderIcon :size="19" />
      <span>资产</span>
    </RouterLink>
    <RouterLink class="rail-item" :class="{ active: isCapability }" to="/capability">
      <SlidersHorizontalIcon :size="19" />
      <span>能力配置</span>
    </RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { FolderIcon, ImageIcon, SlidersHorizontalIcon, SparklesIcon } from '@lucide/vue';
import { computed } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

const route = useRoute();
const isGenerate = computed(() => route.path.startsWith('/generate') || route.path.startsWith('/workspace'));
const isAssets = computed(() => route.path.startsWith('/assets'));
const isCapability = computed(() => route.path.startsWith('/capability'));
</script>
