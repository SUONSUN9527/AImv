import type { RouteRecordRaw } from 'vue-router';
import AssetsPage from '../pages/AssetsPage.vue';
import CapabilityConfigPage from '../pages/CapabilityConfigPage.vue';
import GeneratePage from '../pages/GeneratePage.vue';
import UnsupportedEditPage from '../pages/UnsupportedEditPage.vue';
import WorkspacePage from '../pages/WorkspacePage.vue';

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/generate' },
  { path: '/generate', component: GeneratePage },
  { path: '/workspace/:chainRunId?', component: WorkspacePage },
  { path: '/assets', component: AssetsPage },
  { path: '/capability', component: CapabilityConfigPage },
  { path: '/edit', component: UnsupportedEditPage }
];
