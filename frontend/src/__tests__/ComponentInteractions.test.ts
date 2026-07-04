import { fireEvent, render, screen, waitFor, within } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { createPinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import { afterEach } from 'vitest';
import ApiConfigPanel from '../components/capability/ApiConfigPanel.vue';
import ModePicker from '../components/composer/ModePicker.vue';
import PromptComposer from '../components/composer/PromptComposer.vue';
import CollapsedSidebarActions from '../components/shell/CollapsedSidebarActions.vue';
import CreationSidebar from '../components/shell/CreationSidebar.vue';
import AssetsPage from '../pages/AssetsPage.vue';
import WorkspacePage from '../pages/WorkspacePage.vue';
import { routes } from '../router/routes';
import { useApiConfigStore } from '../stores/useApiConfigStore';
import { useChainRunStore } from '../stores/useChainRunStore';
import { useShellStore } from '../stores/useShellStore';
import type { ChainRun } from '../types/api';

const selectedKey = {
  apiKeyId: 'key-1',
  chainType: 'IMAGE',
  capabilityType: 'llm.text.free',
  label: 'fixture',
  provider: 'fixture-free',
  maskedKey: '****1234',
  status: 'ACTIVE',
  isSelected: true,
  lastVerifiedAt: '2026-07-02T00:00:00Z',
  freeModelGateStatus: 'PASSED'
};

function imageSlot() {
  return {
    chainType: 'IMAGE',
    capabilityType: 'llm.text.free',
    label: '文本规划 LLM',
    required: true,
    keys: [selectedKey]
  };
}

function artifact(kind: 'FinalImageArtifact' | 'FinalVideoArtifact' | 'VideoReviewReport' | 'ImageCandidateAssets') {
  return {
    artifactId: kind,
    chainRunId: 'chain-1',
    chainType: kind.includes('Image') ? 'IMAGE' : 'VIDEO',
    artifactKind: kind,
    displayName: kind === 'FinalImageArtifact'
      ? '最终图片'
      : kind === 'FinalVideoArtifact'
        ? '最终视频'
        : kind === 'ImageCandidateAssets'
          ? '图片候选'
          : '视频验收报告',
    url: kind === 'FinalVideoArtifact' ? '/assets/final.mp4' : `/assets/${kind}.png`,
    contentHash: 'sha256-fixture',
    metadata: {},
    createdAt: '2026-07-02T00:00:00Z'
  };
}

function sidebarChainRun(
  chainRunId: string,
  userGoal: string,
  createdAt: string,
  status: ChainRun['status'] = 'SUCCEEDED'
): ChainRun {
  return {
    chainRunId,
    projectId: `${chainRunId}-project`,
    chainType: 'IMAGE',
    userGoal,
    status,
    currentStageCode: 'I60',
    blockingReason: null,
    createdAt,
    updatedAt: createdAt,
    stageRuns: [],
    artifacts: []
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage?.clear();
});

describe('component interactions', () => {
  it('emits chain mode changes from the mode picker', async () => {
    const { emitted } = render(ModePicker, {
      props: { modelValue: 'IMAGE' }
    });

    await userEvent.click(screen.getByRole('button', { name: '视频生成' }));

    expect(emitted('update:modelValue')).toEqual([['VIDEO']]);
  });

  it('does not submit the composer while an IME composition is active', async () => {
    const { emitted } = render(PromptComposer, {
      props: {
        modelValue: '生成一张都市悬疑短剧封面',
        chainType: 'IMAGE'
      }
    });

    const textarea = screen.getByLabelText('创作目标');

    await fireEvent.keyDown(textarea, { key: 'Enter', isComposing: true });

    expect(emitted('submit')).toBeUndefined();

    await fireEvent.keyDown(textarea, { key: 'Enter' });

    expect(emitted('submit')).toEqual([['生成一张都市悬疑短剧封面']]);
  });

  it('expands the collapsed sidebar action and clamps shell width changes', async () => {
    const pinia = createPinia();
    render(CollapsedSidebarActions, {
      global: {
        plugins: [pinia, createRouter({ history: createWebHistory(), routes })]
      }
    });
    const shell = useShellStore();
    shell.collapseSidebar();
    shell.setSidebarWidth(999);

    await userEvent.click(screen.getByRole('button', { name: '展开对话' }));

    expect(shell.sidebarCollapsed).toBe(false);
    expect(shell.sidebarWidth).toBe(280);
    shell.setSidebarWidth(10);
    expect(shell.sidebarWidth).toBe(180);
  });

  it('resizes the creation sidebar with pointer drag and clamps the documented bounds', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({ success: true, data: [], error: null })
    ));
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(CreationSidebar, {
      global: {
        plugins: [pinia, router]
      }
    });
    const shell = useShellStore();
    shell.setSidebarWidth(200);
    const resizer = screen.getByRole('separator', { name: '调整侧边栏宽度' });

    await fireEvent.pointerDown(resizer, { clientX: 100, pointerId: 1 });
    await fireEvent.pointerMove(window, { clientX: 180, pointerId: 1 });

    expect(shell.sidebarWidth).toBe(280);

    await fireEvent.pointerMove(window, { clientX: -200, pointerId: 1 });

    expect(shell.sidebarWidth).toBe(180);

    await fireEvent.pointerUp(window, { pointerId: 1 });
  });

  it('loads recent projects into the sidebar history and deduplicates titles', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({
        success: true,
        data: [
          {
            projectId: 'project-3',
            title: '赛博侦探',
            goal: '生成一张赛博侦探短剧封面',
            createdAt: '2026-07-02T00:03:00Z'
          },
          {
            projectId: 'project-2',
            title: '都市悬疑',
            goal: '重复标题不同目标',
            createdAt: '2026-07-02T00:02:00Z'
          },
          {
            projectId: 'project-1',
            title: '都市悬疑',
            goal: '生成一张都市悬疑短剧封面',
            createdAt: '2026-07-02T00:01:00Z'
          }
        ],
        error: null
      })
    ));

    render(CreationSidebar, {
      global: {
        plugins: [createPinia(), createRouter({ history: createWebHistory(), routes })]
      }
    });

    expect(await screen.findByText('赛博侦探')).toBeInTheDocument();
    expect(screen.getAllByText('都市悬疑')).toHaveLength(1);
    expect(screen.queryByText('项目')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /赛博侦探/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('status', { name: '任务进行中' })).not.toBeInTheDocument();
    expect(screen.queryByText('任务进行中')).not.toBeInTheDocument();
  });

  it('renames long sidebar history titles and keeps loaded history order stable', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/projects')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-old/external-jobs')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-old/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-old')) {
        return Response.json({
          success: true,
          data: sidebarChainRun('chain-old', '生成一条很长很长很长的历史对话名称用于截断验证', '2026-07-02T00:00:00Z'),
          error: null
        });
      }
      throw new Error(`unexpected request ${url}`);
    }));
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(CreationSidebar, {
      global: {
        plugins: [pinia, router]
      }
    });
    const chainRuns = useChainRunStore();
    const newer = sidebarChainRun('chain-new', '最新对话', '2026-07-02T00:01:00Z');
    const older = sidebarChainRun(
      'chain-old',
      '生成一条很长很长很长的历史对话名称用于截断验证',
      '2026-07-02T00:00:00Z'
    );
    chainRuns.recentRuns = [newer, older];

    expect(await screen.findByRole('link', { name: new RegExp(older.userGoal) })).toBeInTheDocument();

    const olderItem = (await screen.findByRole('link', { name: new RegExp(older.userGoal) })).closest('.history-item');
    expect(olderItem).not.toBeNull();
    await userEvent.click(within(olderItem as HTMLElement).getByRole('button', { name: '修改对话名称' }));
    await userEvent.clear(screen.getByLabelText('对话名称'));
    await userEvent.type(screen.getByLabelText('对话名称'), '短剧封面');
    await userEvent.keyboard('{Enter}');

    expect(screen.getByText('短剧封面')).toBeInTheDocument();
    expect(screen.queryByText(older.userGoal)).not.toBeInTheDocument();

    await chainRuns.load('chain-old');

    expect(chainRuns.recentRuns.map((run) => run.chainRunId)).toEqual(['chain-new', 'chain-old']);
  });

  it('opens a Codex-like history context menu with pin and delete only', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({ success: true, data: [], error: null })
    ));
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(CreationSidebar, {
      global: {
        plugins: [pinia, router]
      }
    });
    const chainRuns = useChainRunStore();
    const newer = sidebarChainRun('chain-new', '最新对话', '2026-07-02T00:01:00Z');
    const older = sidebarChainRun('chain-old', '旧对话', '2026-07-02T00:00:00Z', 'EXECUTING');
    chainRuns.recentRuns = [newer, older];
    const history = screen.getByLabelText('对话');

    expect(await within(history).findByRole('status', { name: '任务完成' })).toBeInTheDocument();
    expect(await within(history).findByRole('status', { name: '任务进行中' })).toBeInTheDocument();
    expect(within(history).queryByText('任务完成')).not.toBeInTheDocument();
    expect(within(history).queryByText('任务进行中')).not.toBeInTheDocument();

    await fireEvent.contextMenu(await within(history).findByRole('link', { name: /旧对话/ }), {
      clientX: 96,
      clientY: 120
    });

    const menu = screen.getByRole('menu');
    expect(within(menu).getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['置顶', '删除']);

    await userEvent.click(within(menu).getByRole('menuitem', { name: '置顶' }));

    expect(within(history).getAllByRole('link').map((link) => link.getAttribute('aria-label')))
      .toEqual(['旧对话，任务进行中', '最新对话，任务完成']);

    await router.push('/workspace/chain-new');
    await waitFor(() => {
      expect(within(history).queryByRole('status', { name: '任务完成' })).not.toBeInTheDocument();
    });
    expect(within(history).getByRole('link', { name: '最新对话' })).toBeInTheDocument();

    await fireEvent.contextMenu(within(history).getByRole('link', { name: /旧对话/ }), {
      clientX: 96,
      clientY: 120
    });
    await userEvent.click(within(screen.getByRole('menu')).getByRole('menuitem', { name: '删除' }));

    expect(within(history).queryByRole('link', { name: /旧对话/ })).not.toBeInTheDocument();
    expect(within(history).getByRole('link', { name: /最新对话/ })).toBeInTheDocument();
  });

  it('tests, selects, deletes, and reloads keys from the capability panel', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api-keys/key-1:verify') && init?.method === 'POST') {
        return Response.json({ success: true, data: selectedKey, error: null });
      }
      if (url.includes('/api-keys/key-1:select') && init?.method === 'POST') {
        return Response.json({ success: true, data: selectedKey, error: null });
      }
      if (url.includes('/api-keys/key-1') && init?.method === 'DELETE') {
        return Response.json({ success: true, data: null, error: null });
      }
      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({ success: true, data: [imageSlot()], error: null });
      }
      if (url.includes('/api/api-configs?chainType=VIDEO')) {
        return Response.json({
          success: true,
          data: [{ ...imageSlot(), chainType: 'VIDEO', capabilityType: 'video.generate.full_with_voice.free' }],
          error: null
        });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    const pinia = createPinia();
    render(ApiConfigPanel, {
      props: { chainType: 'IMAGE' },
      global: { plugins: [pinia] }
    });
    const store = useApiConfigStore();
    store.slotsByChain.IMAGE = [imageSlot()];

    await userEvent.click(await screen.findByRole('button', { name: '测试' }));
    await userEvent.click(await screen.findByRole('button', { name: '设为使用中' }));
    await userEvent.click(await screen.findByRole('button', { name: '删除' }));
    await userEvent.click(screen.getByRole('button', { name: '视频链路' }));

    expect(await screen.findByText('video.generate.full_with_voice.free')).toBeInTheDocument();
  });

  it('shows a safe error when deleting the selected key is rejected', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api-keys/key-1') && init?.method === 'DELETE') {
        return Response.json(
          {
            success: false,
            data: null,
            error: {
              code: 'DELETE_SELECTED_KEY_REJECTED',
              message: '不能删除正在使用中的唯一 key，请先选择另一个可用 key'
            }
          },
          { status: 409 }
        );
      }
      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({ success: true, data: [imageSlot()], error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    const pinia = createPinia();
    render(ApiConfigPanel, {
      props: { chainType: 'IMAGE' },
      global: { plugins: [pinia] }
    });
    const store = useApiConfigStore();
    store.slotsByChain.IMAGE = [imageSlot()];

    await userEvent.click(await screen.findByRole('button', { name: '删除' }));

    expect(await screen.findByText('不能删除正在使用中的唯一 key，请先选择另一个可用 key'))
      .toBeInTheDocument();
  });

  it('renders every configured key field required by the capability docs', async () => {
    const documentedKey = {
      ...selectedKey,
      label: 'dashscope-main',
      provider: 'dashscope-free',
      maskedKey: '****abcd',
      status: 'ACTIVE',
      freeModelGateStatus: 'PASSED'
    };
    const pinia = createPinia();
    render(ApiConfigPanel, {
      props: { chainType: 'IMAGE' },
      global: { plugins: [pinia] }
    });
    const store = useApiConfigStore();
    store.slotsByChain.IMAGE = [{ ...imageSlot(), keys: [documentedKey] }];

    expect(await screen.findByText('dashscope-free')).toBeInTheDocument();
    expect(screen.getByText('dashscope-main')).toBeInTheDocument();
    expect(screen.getByText('****abcd')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
    expect(screen.getByText('当前使用中')).toBeInTheDocument();
    expect(screen.getByText('2026-07-02T00:00:00Z')).toBeInTheDocument();
    expect(screen.getByText('PASSED')).toBeInTheDocument();
  });

  it('shows image and video assets by default while hiding reports and candidates', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({
        success: true,
        data: [
          artifact('FinalImageArtifact'),
          artifact('FinalVideoArtifact'),
          artifact('VideoReviewReport'),
          artifact('ImageCandidateAssets')
        ],
        error: null
      })
    ));

    render(AssetsPage);

    await screen.findByText('最终图片');
    expect(screen.getByText('最终视频')).toBeInTheDocument();
    expect(screen.queryByText('视频验收报告')).not.toBeInTheDocument();
    expect(screen.queryByText('图片候选')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '报告' })).not.toBeInTheDocument();
  });

  it('closes the asset preview modal with Escape', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({
        success: true,
        data: [artifact('FinalImageArtifact')],
        error: null
      })
    ));

    render(AssetsPage);

    await userEvent.click(await screen.findByRole('button', { name: /最终图片/ }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await fireEvent.keyDown(window, { key: 'Enter' });

    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await fireEvent.keyDown(window, { key: 'Escape' });

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('returns to capability config when bottom composer cannot start because selected keys are missing', async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({
        success: true,
        data: [{ ...imageSlot(), keys: [] }],
        error: null
      })
    );
    vi.stubGlobal('fetch', fetchMock);
    const router = createRouter({ history: createWebHistory(), routes });
    window.history.pushState({}, '', '/workspace');
    render(WorkspacePage, {
      global: {
        plugins: [createPinia(), router]
      }
    });

    await userEvent.type(screen.getByLabelText('创作目标'), '再次生成图片');
    await userEvent.click(screen.getByRole('button', { name: '启动生成' }));

    await waitFor(() => expect(window.location.pathname).toBe('/capability'));
  });
});
