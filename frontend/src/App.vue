<template>
  <JimengShell>
    <RouterView />
  </JimengShell>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router';
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import JimengShell from './components/shell/JimengShell.vue';

const route = useRoute();
const router = useRouter();

onMounted(() => {
  const path = window.location.pathname;
  if (route.path !== path) {
    void router.replace(path);
  }
});
</script>
