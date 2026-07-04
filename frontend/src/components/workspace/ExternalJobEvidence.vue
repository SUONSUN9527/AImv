<template>
  <section v-if="jobs.length > 0" class="external-job-evidence" aria-label="外部任务证据">
    <header>
      <h2>任务证据</h2>
      <span>{{ jobs.length }}</span>
    </header>
    <ol>
      <li v-for="job in jobs" :key="job.externalJobId">
        <strong>{{ maskProviderJobId(job.providerJobId) }}</strong>
        <span>{{ job.status }}</span>
        <small>{{ job.retryPolicy }}</small>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import type { ExternalJob } from '../../types/api';
import { maskProviderJobId } from '../../utils/providerJobs';

defineProps<{
  jobs: ExternalJob[];
}>();
</script>
