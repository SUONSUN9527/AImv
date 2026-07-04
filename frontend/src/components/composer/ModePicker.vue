<template>
  <div class="mode-picker" role="group" aria-label="生成链路">
    <button
      type="button"
      :class="{ active: modelValue === 'IMAGE' }"
      aria-label="图片生成"
      @click="$emit('update:modelValue', 'IMAGE')"
    >
      <ImageIcon :size="16" />
      <span>图片生成</span>
    </button>
    <button
      type="button"
      :class="{ active: modelValue === 'VIDEO' }"
      aria-label="视频生成"
      @click="$emit('update:modelValue', 'VIDEO')"
    >
      <VideoIcon :size="16" />
      <span>视频生成</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ImageIcon, VideoIcon } from '@lucide/vue';
import type { ChainType } from '../../types/api';

defineProps<{
  modelValue: ChainType;
}>();

defineEmits<{
  'update:modelValue': [value: ChainType];
}>();
</script>
