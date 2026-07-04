<template>
  <div class="result-strip" aria-label="生成结果">
    <article v-for="artifact in visibleArtifacts" :key="artifact.artifactId" class="result-card">
      <div class="result-media">
        <video
          v-if="isVideoArtifact(artifact)"
          :src="artifact.url"
          :aria-label="mediaLabel"
          controls
          playsinline
          preload="metadata"
          @error="markMediaError(artifact.artifactId)"
        />
        <img
          v-else
          :src="artifact.url"
          :alt="mediaLabel"
          loading="lazy"
          @click="preview = artifact"
          @error="markMediaError(artifact.artifactId)"
        />
        <button
          v-if="!mediaErrors.has(artifact.artifactId)"
          type="button"
          class="result-zoom"
          aria-label="放大查看"
          @click="preview = artifact"
        >
          ⤢ 放大
        </button>
        <span v-if="mediaErrors.has(artifact.artifactId)" class="media-error">预览不可用</span>
      </div>
    </article>

    <!-- 点击放大：和资产库一致的灯箱预览 -->
    <div
      v-if="preview"
      class="result-lightbox"
      role="dialog"
      aria-modal="true"
      aria-label="放大预览"
      @click.self="preview = null"
      @keydown.esc="preview = null"
    >
      <button type="button" class="result-lightbox-close" aria-label="关闭" @click="preview = null">✕</button>
      <video
        v-if="preview && isVideoArtifact(preview)"
        :src="preview.url"
        controls
        autoplay
        playsinline
        class="result-lightbox-media"
      />
      <img v-else-if="preview" :src="preview.url" :alt="mediaLabel" class="result-lightbox-media" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import type { Artifact, ChainType } from '../../types/api';

const props = defineProps<{
  artifacts: Artifact[];
  chainType: ChainType;
}>();
const mediaErrors = ref(new Set<string>());
const preview = ref<Artifact | null>(null);
const finalArtifactKindByChain = {
  IMAGE: 'FinalImageArtifact',
  VIDEO: 'FinalVideoArtifact'
} as const satisfies Record<ChainType, Artifact['artifactKind']>;
const mediaLabel = computed(() => (props.chainType === 'VIDEO' ? '生成视频结果' : '生成图片结果'));

const visibleArtifacts = computed(() =>
  props.artifacts
    .filter((artifact) => artifact.artifactKind === finalArtifactKindByChain[props.chainType])
    .slice(0, 4)
);

function isVideoArtifact(artifact: Artifact) {
  return artifact.artifactKind === 'FinalVideoArtifact';
}

function markMediaError(artifactId: string) {
  mediaErrors.value = new Set([...mediaErrors.value, artifactId]);
}
</script>

<style scoped>
.result-zoom {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 4px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(16, 24, 40, 0.72);
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.result-zoom:hover {
  background: rgba(16, 24, 40, 0.9);
}
.result-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.82);
  padding: 4vh 4vw;
}
.result-lightbox-media {
  max-width: 92vw;
  max-height: 92vh;
  object-fit: contain;
  border-radius: 8px;
}
.result-lightbox-close {
  position: fixed;
  top: 20px;
  right: 24px;
  width: 40px;
  height: 40px;
  font-size: 18px;
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}
</style>
