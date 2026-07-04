<template>
  <ol class="stage-timeline" aria-label="阶段进度">
    <li v-for="stage in stages" :key="stage.stageRunId">
      <span>{{ stage.stageCode }}</span>
      <strong>{{ stage.stageName }}</strong>
      <small>{{ stage.reviewReport.overallScore }}</small>
    </li>
  </ol>
</template>

<script setup lang="ts">
import type { StageRun } from '../../types/api';

defineProps<{
  stages: StageRun[];
}>();
</script>
