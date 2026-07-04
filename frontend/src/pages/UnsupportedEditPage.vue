<template>
  <section class="unsupported-edit-page">
    <div class="unsupported-layout">
      <aside class="disabled-panel">
        <h2>资源库</h2>
        <p>当前版本只展示最终图片、完整视频和报告。</p>
      </aside>
      <main>
        <h1>当前后端链路不支持剪辑</h1>
        <p>视频链路必须直接输出完整 10 秒、9:16、带人声配音短片，不提供拆片段拼接、独立音频或口型同步补救。</p>
        <RouterLink class="primary-link" to="/generate">回到生成</RouterLink>
      </main>
      <aside class="disabled-panel">
        <h2>交付</h2>
        <p>最终成片由视频链路验收交付。</p>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router';
</script>
