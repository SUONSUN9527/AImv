<template>
  <section class="assets-page">
    <header class="assets-hero">
      <div>
        <p>资产</p>
        <h1>全部作品</h1>
        <span>图片与视频作品</span>
      </div>
      <div class="asset-tabs" role="tablist" aria-label="资产分类">
        <button
          v-for="tab in tabs"
          :key="tab"
          type="button"
          :class="{ active: activeTab === tab }"
          @click="activeTab = tab"
        >
          {{ tab }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="asset-grid" aria-busy="true" aria-label="正在加载资产">
      <div v-for="index in 8" :key="index" class="asset-card asset-card-skeleton" />
    </div>

    <p v-else-if="loadError" class="error-line">{{ loadError }}</p>

    <div v-else-if="filteredArtifacts.length === 0" class="asset-empty" role="status">
      <strong>暂无图片或视频资产</strong>
      <span>完成一次图片或视频生成后，最终产物会出现在这里。</span>
    </div>

    <div v-else class="asset-grid">
      <button
        v-for="artifact in filteredArtifacts"
        :key="artifact.artifactId"
        class="asset-card"
        type="button"
        :aria-label="`${artifact.displayName} ${assetTypeLabel(artifact)}`"
        @click="preview = artifact"
      >
        <span class="asset-media">
          <img
            v-if="isImageArtifact(artifact)"
            :src="artifact.url"
            :alt="artifact.displayName"
            @error="markMediaError(artifact.artifactId)"
          />
          <video
            v-else
            :src="artifact.url"
            preload="auto"
            muted
            playsinline
            aria-label="视频封面"
            @loadedmetadata="seekVideoCover"
            @error="markMediaError(artifact.artifactId)"
          />
          <span v-if="mediaErrors.has(artifact.artifactId)" class="media-error">预览不可用</span>
          <span class="asset-badge">{{ assetTypeLabel(artifact) }}</span>
        </span>
        <span class="asset-card-body">
          <strong>{{ artifact.displayName }}</strong>
          <small>{{ formatDate(artifact.createdAt) }}</small>
        </span>
      </button>
    </div>

    <div v-if="preview" class="preview-modal" role="dialog" aria-modal="true" @click.self="preview = null">
      <article class="preview-panel">
        <header>
          <div>
            <span>{{ assetTypeLabel(preview) }}</span>
            <h2>{{ preview.displayName }}</h2>
          </div>
          <button type="button" aria-label="关闭预览" @click="preview = null">关闭</button>
        </header>
        <img
          v-if="isImageArtifact(preview)"
          class="preview-image"
          :src="preview.url"
          :alt="preview.displayName"
        />
        <video
          v-else
          ref="previewVideo"
          aria-label="最终视频预览"
          class="preview-video"
          controls
          playsinline
          :src="preview.url"
          @loadedmetadata="applyPlaybackRate"
        />
        <div v-if="isVideoArtifact(preview)" class="speed-controls" role="group" aria-label="播放速度">
          <button
            v-for="speed in playbackSpeeds"
            :key="speed"
            type="button"
            :aria-pressed="playbackRate === speed"
            :class="{ active: playbackRate === speed }"
            @click="setPlaybackRate(speed)"
          >
            {{ speed }}x
          </button>
        </div>
        <dl v-if="previewMetadataEntries.length" class="preview-metadata" aria-label="资产元数据">
          <template v-for="entry in previewMetadataEntries" :key="entry.key">
            <dt>{{ entry.key }}</dt>
            <dd>{{ entry.value }}</dd>
          </template>
        </dl>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { listArtifacts } from '../api/artifacts';
import type { Artifact } from '../types/api';

type FinalAsset = Artifact & {
  artifactKind: 'FinalImageArtifact' | 'FinalVideoArtifact';
};

const tabs = ['全部', '图片', '视频'] as const;
const playbackSpeeds = [0.5, 1, 1.5, 2] as const;
const activeTab = ref<(typeof tabs)[number]>('全部');
const artifacts = ref<Artifact[]>([]);
const loading = ref(true);
const loadError = ref('');
const mediaErrors = ref(new Set<string>());
const preview = ref<FinalAsset | null>(null);
const previewVideo = ref<HTMLVideoElement | null>(null);
const playbackRate = ref<(typeof playbackSpeeds)[number]>(1);
const mediaArtifacts = computed(() => artifacts.value.filter(isFinalAsset));
const filteredArtifacts = computed(() => mediaArtifacts.value.filter((artifact) => {
  if (activeTab.value === '图片') {
    return isImageArtifact(artifact);
  }
  if (activeTab.value === '视频') {
    return isVideoArtifact(artifact);
  }
  return true;
}));
const previewMetadataEntries = computed(() => Object.entries(preview.value?.metadata ?? {})
  .filter(([key]) => key !== 'summary')
  .map(([key, value]) => ({ key, value: metadataValue(value) })));

async function loadArtifacts() {
  loading.value = true;
  loadError.value = '';
  try {
    artifacts.value = await listArtifacts();
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '资产加载失败';
  } finally {
    loading.value = false;
  }
}

function isFinalAsset(artifact: Artifact): artifact is FinalAsset {
  return artifact.artifactKind === 'FinalImageArtifact' || artifact.artifactKind === 'FinalVideoArtifact';
}

function isImageArtifact(artifact: FinalAsset) {
  return artifact.artifactKind === 'FinalImageArtifact';
}

function isVideoArtifact(artifact: FinalAsset) {
  return artifact.artifactKind === 'FinalVideoArtifact';
}

function assetTypeLabel(artifact: FinalAsset) {
  return isImageArtifact(artifact) ? '图片' : '视频';
}

function markMediaError(artifactId: string) {
  mediaErrors.value = new Set([...mediaErrors.value, artifactId]);
}

function seekVideoCover(event: Event) {
  const video = event.currentTarget;
  if (!(video instanceof HTMLVideoElement) || !Number.isFinite(video.duration) || video.duration <= 0.12) {
    return;
  }
  video.currentTime = 0.1;
}

function applyPlaybackRate() {
  if (previewVideo.value) {
    previewVideo.value.playbackRate = playbackRate.value;
  }
}

function setPlaybackRate(speed: (typeof playbackSpeeds)[number]) {
  playbackRate.value = speed;
  applyPlaybackRate();
}

function metadataValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (value !== null && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function closePreviewOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    preview.value = null;
  }
}

onMounted(() => {
  window.addEventListener('keydown', closePreviewOnEscape);
  void loadArtifacts();
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', closePreviewOnEscape);
});
</script>
