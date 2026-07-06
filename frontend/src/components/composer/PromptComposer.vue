<template>
  <form class="prompt-composer" @submit.prevent="submit">
    <label class="sr-only" for="creative-goal">创作目标</label>
    <textarea
      id="creative-goal"
      v-model="localValue"
      aria-label="创作目标"
      placeholder="输入想法、剧本或提示词，和 Agent 一起创作"
      rows="4"
      @keydown="onKeydown"
    />
    <div class="composer-actions">
      <ModePicker :model-value="chainType" @update:model-value="$emit('update:chainType', $event)" />
      <button class="send-button" type="submit" aria-label="启动生成" :disabled="submitting || !localValue.trim()">
        <SendIcon :size="18" />
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { SendIcon } from '@lucide/vue';
import { ref, watch } from 'vue';
import type { ChainType } from '../../types/api';
import ModePicker from './ModePicker.vue';

const props = defineProps<{
  modelValue: string;
  chainType: ChainType;
  submitting?: boolean;
}>();

const emit = defineEmits<{
  submit: [value: string];
  'update:modelValue': [value: string];
  'update:chainType': [value: ChainType];
}>();

const localValue = ref(props.modelValue);

watch(() => props.modelValue, (value) => {
  localValue.value = value;
});

watch(localValue, (value) => {
  emit('update:modelValue', value);
});

function submit() {
  const value = localValue.value.trim();
  if (value) {
    emit('submit', value);
    localValue.value = '';
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) {
    return;
  }
  if (event.isComposing || event.keyCode === 229) {
    return;
  }
  event.preventDefault();
  submit();
}
</script>
