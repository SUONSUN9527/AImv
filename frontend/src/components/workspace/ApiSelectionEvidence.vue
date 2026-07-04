<template>
  <section v-if="snapshots.length > 0" class="api-selection-evidence" aria-label="API 选择快照">
    <header>
      <h2>API 选择快照</h2>
      <span>{{ snapshots.length }}</span>
    </header>
    <ol>
      <li v-for="snapshot in snapshots" :key="snapshot.snapshotId">
        <strong>{{ snapshot.capabilityType }}</strong>
        <dl>
          <div>
            <dt>Provider</dt>
            <dd>{{ snapshot.provider }}</dd>
          </div>
          <div>
            <dt>Model</dt>
            <dd>{{ snapshot.model ?? '-' }}</dd>
          </div>
          <div>
            <dt>Key</dt>
            <dd>{{ snapshot.maskedKey }}</dd>
          </div>
          <div>
            <dt>Quota</dt>
            <dd>{{ snapshot.freeModelGate.quotaSnapshot }}</dd>
          </div>
        </dl>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import type { ApiSelectionSnapshot } from '../../types/api';

defineProps<{
  snapshots: ApiSelectionSnapshot[];
}>();
</script>
