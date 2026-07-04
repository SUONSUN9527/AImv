import { render, screen, waitFor } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { createPinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import { afterEach } from 'vitest';
import { apiRequest, ApiClientError } from '../api/http';
import ResultStrip from '../components/workspace/ResultStrip.vue';
import AssetsPage from '../pages/AssetsPage.vue';
import WorkspacePage from '../pages/WorkspacePage.vue';
import { routes } from '../router/routes';
import { useChainRunStore } from '../stores/useChainRunStore';

type StatusFixture =
  | 'WAITING_CAPABILITY'
  | 'WAITING_USER'
  | 'WAITING_REVIEW'
  | 'EXECUTING'
  | 'QUALITY_REVIEWING'
  | 'HANDOFF_WRITING'
  | 'KNOWLEDGE_INGESTING'
  | 'FAILED'
  | 'CANCELLED';

function chainRun(status: StatusFixture) {
  const blockingReason = {
    EXECUTING: null,
    QUALITY_REVIEWING: null,
    HANDOFF_WRITING: null,
    KNOWLEDGE_INGESTING: null,
    FAILED: 'CONTENT_SAFETY_REJECTED',
    CANCELLED: null,
    WAITING_CAPABILITY: '缺少完整视频带配音免费能力',
    WAITING_USER: '没有其他完整视频 provider，等待用户重新选择',
    WAITING_REVIEW: 'RAG 证据字段冲突，等待人工确认'
  }[status];

  return {
    chainRunId: `chain-${status}`,
    projectId: 'project-1',
    chainType: 'VIDEO',
    userGoal: '生成完整短片',
    status,
    currentStageCode: 'V30',
    blockingReason,
    createdAt: '2026-07-02T00:00:00Z',
    updatedAt: '2026-07-02T00:00:00Z',
    stageRuns: [],
    artifacts: []
  };
}

function artifact(
  displayName: string,
  artifactKind: 'FinalImageArtifact' | 'FinalVideoArtifact' | 'VideoReviewReport'
) {
  return {
    artifactId: displayName,
    chainRunId: 'chain-1',
    chainType: artifactKind.includes('Image') ? 'IMAGE' : 'VIDEO',
    artifactKind,
    displayName,
    url: `/assets/${displayName}`,
    contentHash: 'sha256-fixture',
    metadata: {},
    createdAt: '2026-07-02T00:00:00Z'
  };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('branch coverage for API and status states', () => {
  it('throws structured API errors without exposing transport details', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json(
        { success: false, data: null, error: { code: 'FREE_MODEL_GATE_FAILED', message: '免费额度不足' } },
        { status: 409 }
      )
    ));

    await expect(apiRequest('/api/fails')).rejects.toMatchObject<ApiClientError>({
      code: 'FREE_MODEL_GATE_FAILED',
      message: '免费额度不足'
    });
  });

  it('renders waiting states and executing status copy branches', async () => {
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(WorkspacePage, {
      global: { plugins: [pinia, router] }
    });
    const store = useChainRunStore();
    store.activeChainRun = chainRun('WAITING_CAPABILITY');

    expect(await screen.findByText('缺少完整视频带配音免费能力')).toBeInTheDocument();

    store.activeChainRun = chainRun('WAITING_USER');
    await waitFor(() => {
      expect(screen.getByText('没有其他完整视频 provider，等待用户重新选择')).toBeInTheDocument();
    });

    store.activeChainRun = chainRun('WAITING_REVIEW');
    await waitFor(() => {
      expect(screen.getByText('RAG 证据字段冲突，等待人工确认')).toBeInTheDocument();
    });

    store.activeChainRun = chainRun('EXECUTING');
    await waitFor(() => {
      expect(screen.getByText('正在生成中。')).toBeInTheDocument();
    });
  });

  it('renders the documented workspace loading skeleton', async () => {
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(WorkspacePage, {
      global: { plugins: [pinia, router] }
    });
    const store = useChainRunStore();
    store.loading = true;

    expect(await screen.findByLabelText('生成加载骨架')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '生成加载素材' }))
      .toHaveAttribute('src', '/history-source.png');
  });

  it('limits the workspace result strip to four media results without visible artifact names', async () => {
    render(ResultStrip, {
      props: {
        chainType: 'IMAGE',
        artifacts: [
          artifact('结果1', 'FinalImageArtifact'),
          artifact('结果2', 'FinalImageArtifact'),
          artifact('结果3', 'FinalImageArtifact'),
          artifact('结果4', 'FinalImageArtifact'),
          artifact('结果5', 'FinalImageArtifact')
        ]
      }
    });

    expect(screen.getAllByRole('img', { name: '生成图片结果' })).toHaveLength(4);
    expect(screen.queryByText('结果1')).not.toBeInTheDocument();
    expect(screen.queryByText('结果5')).not.toBeInTheDocument();
  });

  it('opens chain-scoped API config from WAITING_CAPABILITY workspace state', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/api-configs?chainType=VIDEO')) {
        return Response.json({
          success: true,
          data: [
            {
              chainType: 'VIDEO',
              capabilityType: 'video.generate.full_with_voice.free',
              label: '完整视频生成',
              required: true,
              keys: []
            }
          ],
          error: null
        });
      }
      throw new Error(`unexpected request ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(WorkspacePage, {
      global: { plugins: [pinia, router] }
    });
    const store = useChainRunStore();
    store.activeChainRun = chainRun('WAITING_CAPABILITY');

    expect(await screen.findByText('缺少完整视频带配音免费能力')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '去配置' }));

    await waitFor(() => expect(window.location.pathname).toBe('/capability'));
  });

  it('renders documented review, handoff, failed, and cancelled status copy', async () => {
    const pinia = createPinia();
    const router = createRouter({ history: createWebHistory(), routes });
    render(WorkspacePage, {
      global: { plugins: [pinia, router] }
    });
    const store = useChainRunStore();

    store.activeChainRun = chainRun('QUALITY_REVIEWING');
    expect(await screen.findByText('正在验收结果')).toBeInTheDocument();

    store.activeChainRun = chainRun('HANDOFF_WRITING');
    await waitFor(() => {
      expect(screen.getByText('正在整理交付信息')).toBeInTheDocument();
    });

    store.activeChainRun = chainRun('KNOWLEDGE_INGESTING');
    await waitFor(() => {
      expect(screen.getByText('正在整理交付信息')).toBeInTheDocument();
    });

    store.activeChainRun = chainRun('FAILED');
    await waitFor(() => {
      expect(screen.getByText('链路执行失败，可点击再次生成重试：CONTENT_SAFETY_REJECTED'))
        .toBeInTheDocument();
    });

    store.activeChainRun = chainRun('CANCELLED');
    await waitFor(() => {
      expect(screen.getByText('链路已取消，可重新生成。')).toBeInTheDocument();
    });
  });

  it('shows all final image and video assets by default without report or candidate groups', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({
        success: true,
        data: [
          artifact('最终图片', 'FinalImageArtifact'),
          artifact('最终视频', 'FinalVideoArtifact'),
          artifact('视频报告', 'VideoReviewReport')
        ],
        error: null
      })
    ));

    render(AssetsPage);

    await screen.findByText('最终图片');
    expect(screen.getByText('最终视频')).toBeInTheDocument();
    expect(screen.queryByText('视频报告')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '报告' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '视频' }));
    expect(screen.getByText('最终视频')).toBeInTheDocument();
    expect(screen.queryByText('最终图片')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '全部' }));
    expect(screen.getByText('最终图片')).toBeInTheDocument();
    expect(screen.queryByText('音乐')).not.toBeInTheDocument();
    expect(screen.queryByText('音频')).not.toBeInTheDocument();
  });

  it('renders video assets with real video media and previews playback controls', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({
        success: true,
        data: [artifact('最终视频', 'FinalVideoArtifact')],
        error: null
      })
    ));

    render(AssetsPage);

    const cover = await screen.findByLabelText('视频封面');
    expect(cover).toHaveAttribute('src', '/assets/最终视频');

    await userEvent.click(await screen.findByRole('button', { name: /最终视频/ }));
    const preview = screen.getByLabelText('最终视频预览') as HTMLVideoElement;
    expect(preview).toHaveAttribute('src', '/assets/最终视频');

    await userEvent.click(screen.getByRole('button', { name: '1.5x' }));
    expect(preview.playbackRate).toBe(1.5);
  });
});
