import { render, screen, waitFor, within } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { createPinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import { afterEach } from 'vitest';
import App from '../App.vue';
import ApiConfigPanel from '../components/capability/ApiConfigPanel.vue';
import CreationSidebar from '../components/shell/CreationSidebar.vue';
import AssetsPage from '../pages/AssetsPage.vue';
import WorkspacePage from '../pages/WorkspacePage.vue';
import { routes } from '../router/routes';
import { useApiConfigStore } from '../stores/useApiConfigStore';

function renderApp(path: string) {
  const router = createRouter({
    history: createWebHistory(),
    routes
  });
  window.history.pushState({}, '', path);
  return render(App, {
    global: {
      plugins: [createPinia(), router]
    }
  });
}

function chainRunFixture() {
  return {
    chainRunId: 'chain-1',
    projectId: 'project-1',
    chainType: 'IMAGE',
    userGoal: '生成一张都市悬疑短剧封面',
    status: 'SUCCEEDED',
    currentStageCode: 'I60',
    blockingReason: null,
    createdAt: '2026-07-02T00:00:00Z',
    updatedAt: '2026-07-02T00:00:00Z',
    stageRuns: [
      {
        stageRunId: 'stage-1',
        chainRunId: 'chain-1',
        stageCode: 'I00',
        stageName: '目标锁定',
        status: 'SUCCEEDED',
        reviewReport: { passed: true, overallScore: 95, rubricVersion: 'image-I00.v1', summary: '通过' }
      },
      {
        stageRunId: 'stage-2',
        chainRunId: 'chain-1',
        stageCode: 'I40',
        stageName: '图片生成',
        status: 'SUCCEEDED',
        reviewReport: { passed: true, overallScore: 95, rubricVersion: 'image-I40.v1', summary: '通过' }
      },
      {
        stageRunId: 'stage-3',
        chainRunId: 'chain-1',
        stageCode: 'I60',
        stageName: '图片验收交付',
        status: 'SUCCEEDED',
        reviewReport: {
          passed: true,
          overallScore: 96,
          rubricVersion: 'image-I60.v1',
          summary: '最终验收通过，构图和安全检查达标'
        }
      }
    ],
    artifacts: [
      {
        artifactId: 'artifact-1',
        chainRunId: 'chain-1',
        chainType: 'IMAGE',
        artifactKind: 'FinalImageArtifact',
        displayName: '最终图片',
        url: '/assets/final.svg',
        contentHash: 'sha256-fixture',
        metadata: { aspectRatio: '9:16', candidateCount: 1 },
        createdAt: '2026-07-02T00:00:00Z'
      },
      {
        artifactId: 'artifact-review-1',
        chainRunId: 'chain-1',
        chainType: 'IMAGE',
        artifactKind: 'ImageReviewReport',
        displayName: '图片验收报告',
        url: '/reports/fixture/image-review.json',
        contentHash: 'sha256-review-fixture',
        metadata: { passed: true, summary: 'fixture 合同产物: 生成一张都市悬疑短剧封面' },
        createdAt: '2026-07-02T00:00:00Z'
      }
    ]
  };
}

function succeededVideoChainRunFixture() {
  return {
    ...chainRunFixture(),
    chainRunId: 'chain-video',
    chainType: 'VIDEO',
    userGoal: '生成10秒9:16带人声配音的AI短剧',
    currentStageCode: 'V60',
    stageRuns: [
      {
        ...chainRunFixture().stageRuns[0],
        stageRunId: 'stage-video-1',
        chainRunId: 'chain-video',
        stageCode: 'V40',
        stageName: '完整短片生成'
      },
      {
        ...chainRunFixture().stageRuns[2],
        stageRunId: 'stage-video-2',
        chainRunId: 'chain-video',
        stageCode: 'V60',
        stageName: '视频验收交付',
        reviewReport: {
          passed: true,
          overallScore: 92,
          rubricVersion: 'video-V60.v1',
          summary: '视频验收交付已按固定 rubric 通过'
        }
      }
    ],
    artifacts: [
      {
        artifactId: 'artifact-video-1',
        chainRunId: 'chain-video',
        chainType: 'VIDEO',
        artifactKind: 'FinalVideoArtifact',
        displayName: '最终视频',
        url: '/assets/final-video.mp4',
        contentHash: 'sha256-video-fixture',
        metadata: { durationSeconds: 10, aspectRatio: '9:16', hasHumanVoice: true },
        createdAt: '2026-07-02T00:00:00Z'
      },
      {
        artifactId: 'artifact-video-review-1',
        chainRunId: 'chain-video',
        chainType: 'VIDEO',
        artifactKind: 'VideoReviewReport',
        displayName: '视频验收报告',
        url: '/reports/fixture/video-review.json',
        contentHash: 'sha256-video-review-fixture',
        metadata: { passed: true, shortDramaScore: 92, summary: '完整短片 fixture 合同产物' },
        createdAt: '2026-07-02T00:00:00Z'
      }
    ]
  };
}

function waitingUserChainRunFixture() {
  return {
    ...chainRunFixture(),
    chainRunId: 'chain-waiting',
    chainType: 'VIDEO',
    userGoal: '生成10秒9:16带人声配音的AI短剧',
    status: 'WAITING_USER',
    currentStageCode: 'V30',
    blockingReason: '没有其他完整视频 provider，等待用户重新选择',
    stageRuns: [
      {
        ...chainRunFixture().stageRuns[0],
        stageRunId: 'stage-v00',
        stageCode: 'V00',
        stageName: '目标锁定'
      },
      {
        ...chainRunFixture().stageRuns[1],
        stageRunId: 'stage-v40-original',
        stageCode: 'V40',
        stageName: '完整短片生成'
      },
      {
        ...chainRunFixture().stageRuns[1],
        stageRunId: 'stage-recovery-v30',
        stageCode: 'V30',
        stageName: '视频能力预检',
        status: 'WAITING_USER',
        reviewReport: {
          passed: false,
          overallScore: 0,
          rubricVersion: 'recovery-policy.v1',
          summary: '没有其他完整视频 provider，等待用户重新选择'
        }
      }
    ],
    artifacts: []
  };
}

function executingChainRunFixture() {
  return {
    ...chainRunFixture(),
    status: 'EXECUTING',
    currentStageCode: 'I40',
    artifacts: []
  };
}

function selectedSlots() {
  return [
    {
      chainType: 'IMAGE',
      capabilityType: 'llm.text.free',
      label: '文本规划 LLM',
      required: true,
      keys: [selectedKey('key-1', 'IMAGE', 'llm.text.free')]
    },
    {
      chainType: 'IMAGE',
      capabilityType: 'rag.embedding.free',
      label: 'RAG Embedding',
      required: true,
      keys: [selectedKey('key-2', 'IMAGE', 'rag.embedding.free')]
    },
    {
      chainType: 'IMAGE',
      capabilityType: 'rag.rerank.free',
      label: 'RAG Rerank',
      required: true,
      keys: [selectedKey('key-3', 'IMAGE', 'rag.rerank.free')]
    },
    {
      chainType: 'IMAGE',
      capabilityType: 'image.generate.free',
      label: '图片生成',
      required: true,
      keys: [selectedKey('key-4', 'IMAGE', 'image.generate.free')]
    }
  ];
}

function selectedVideoSlots() {
  return [
    ...selectedSlots().slice(0, 3).map((slot) => ({
      ...slot,
      chainType: 'VIDEO',
      keys: slot.keys.map((key) => ({ ...key, chainType: 'VIDEO' }))
    })),
    {
      chainType: 'VIDEO',
      capabilityType: 'video.generate.full_with_voice.free',
      label: '完整视频生成',
      required: true,
      keys: [selectedKey('key-video', 'VIDEO', 'video.generate.full_with_voice.free')]
    }
  ];
}

function selectedKey(apiKeyId: string, chainType = 'IMAGE', capabilityType = 'llm.text.free') {
  return {
    apiKeyId,
    chainType,
    capabilityType,
    label: 'fixture',
    provider: 'fixture-free',
    maskedKey: '****1234',
    status: 'ACTIVE',
    isSelected: true,
    lastVerifiedAt: '2026-07-02T00:00:00Z',
    freeModelGateStatus: 'PASSED'
  };
}

function externalJobFixture() {
  return [
    {
      externalJobId: 'external-job-1',
      providerJobId: 'provider-job-abcdef1234567890',
      chainRunId: 'chain-1',
      stageRunId: 'stage-2',
      capabilityType: 'image.generate.free',
      provider: 'fixture-free',
      status: 'SUCCEEDED',
      retryPolicy: 'FREE_PROVIDER_RETRY_ONLY',
      retryCount: 0,
      requestHash: 'sha256:abcdef',
      responseMetadata: { adapterKind: 'HTTP_ADAPTER' },
      createdAt: '2026-07-02T00:00:00Z',
      updatedAt: '2026-07-02T00:00:00Z'
    }
  ];
}

function apiSelectionSnapshotsFixture() {
  return [
    {
      snapshotId: 'snapshot-1',
      chainRunId: 'chain-1',
      chainType: 'IMAGE',
      capabilityType: 'llm.text.free',
      provider: 'fixture-free',
      apiKeyId: 'key-1',
      maskedKey: '****1234',
      model: 'free-model',
      freeModelGate: {
        freeModelGateId: 'gate-1',
        passed: true,
        provider: 'fixture-free',
        model: 'free-model',
        capabilityType: 'llm.text.free',
        plan: 'free',
        paidFallbackAllowed: false,
        quotaSnapshot: 'fixture-free-quota:available',
        checkedAt: '2026-07-02T00:00:00Z'
      },
      createdAt: '2026-07-02T00:00:00Z'
    }
  ];
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('frontend chain flows', () => {
  it('starts an image chain through the new chain-run endpoints when required keys are selected', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({ success: true, data: selectedSlots(), error: null });
      }
      if (url.includes('/api/api-configs?chainType=VIDEO')) {
        return Response.json({ success: true, data: selectedVideoSlots(), error: null });
      }
      if (url.endsWith('/api/projects') && !init?.method) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.endsWith('/api/projects') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: {
            projectId: 'project-1',
            title: '都市悬疑',
            goal: '生成一张都市悬疑短剧封面',
            createdAt: '2026-07-02T00:00:00Z'
          },
          error: null
        });
      }
      if (url.includes('/api/projects/project-1/image-chain-runs')) {
        return Response.json({ success: true, data: chainRunFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1/external-jobs')) {
        return Response.json({ success: true, data: externalJobFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1/api-selection-snapshot')) {
        return Response.json({ success: true, data: apiSelectionSnapshotsFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1')) {
        return Response.json({ success: true, data: chainRunFixture(), error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/generate');

    await userEvent.type(await screen.findByLabelText('创作目标'), '生成一张都市悬疑短剧封面');
    await userEvent.click(screen.getByRole('button', { name: '启动生成' }));

    await waitFor(() => expect(window.location.pathname).toBe('/workspace/chain-1'));
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/projects'), expect.anything());
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/image-chain-runs'), expect.anything());
    expect(fetchMock.mock.calls.map((call) => String(call[0])).join('\n')).not.toContain('pipeline-runs');
  });

  it('opens capability config when chain start fails the free model gate', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({ success: true, data: selectedSlots(), error: null });
      }
      if (url.includes('/api/api-configs?chainType=VIDEO')) {
        return Response.json({ success: true, data: selectedVideoSlots(), error: null });
      }
      if (url.endsWith('/api/projects') && !init?.method) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.endsWith('/api/projects') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: {
            projectId: 'project-1',
            title: '都市悬疑',
            goal: '生成一张都市悬疑短剧封面',
            createdAt: '2026-07-02T00:00:00Z'
          },
          error: null
        });
      }
      if (url.includes('/api/projects/project-1/image-chain-runs')) {
        return Response.json(
          {
            success: false,
            data: null,
            error: { code: 'FREE_MODEL_GATE_FAILED', message: '免费额度不足，请重新测试 key' }
          },
          { status: 409 }
        );
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/generate');

    await userEvent.type(await screen.findByLabelText('创作目标'), '生成一张都市悬疑短剧封面');
    await userEvent.click(screen.getByRole('button', { name: '启动生成' }));

    expect(await screen.findByRole('heading', { name: '能力配置' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/capability');
  });

  it('starts a video chain through the documented video-chain endpoint', async () => {
    const videoRun = {
      ...chainRunFixture(),
      chainRunId: 'chain-video',
      chainType: 'VIDEO',
      userGoal: '生成10秒9:16带人声配音的AI短剧',
      currentStageCode: 'V60',
      artifacts: []
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/api-configs?chainType=VIDEO')) {
        return Response.json({ success: true, data: selectedVideoSlots(), error: null });
      }
      if (url.endsWith('/api/projects') && !init?.method) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.endsWith('/api/projects') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: {
            projectId: 'project-video',
            title: '完整短片',
            goal: '生成10秒9:16带人声配音的AI短剧',
            createdAt: '2026-07-02T00:00:00Z'
          },
          error: null
        });
      }
      if (url.includes('/api/projects/project-video/video-chain-runs')) {
        return Response.json({ success: true, data: videoRun, error: null });
      }
      if (url.includes('/api/chain-runs/chain-video/external-jobs')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-video')) {
        return Response.json({ success: true, data: videoRun, error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/generate');

    await userEvent.click(await screen.findByRole('button', { name: '视频生成' }));
    await userEvent.type(await screen.findByLabelText('创作目标'), '生成10秒9:16带人声配音的AI短剧');
    await userEvent.click(screen.getByRole('button', { name: '启动生成' }));

    await waitFor(() => expect(window.location.pathname).toBe('/workspace/chain-video'));
    const requestUrls = fetchMock.mock.calls.map((call) => String(call[0])).join('\n');
    const requestBodies = fetchMock.mock.calls.map((call) => String(call[1]?.body ?? '')).join('\n');
    expect(requestUrls).toContain('/api/projects/project-video/video-chain-runs');
    expect(requestUrls).not.toContain('/image-chain-runs');
    expect(requestUrls).not.toContain('pipeline-runs');
    expect(requestBodies).toContain('生成10秒9:16带人声配音的AI短剧');
  });

  it('adds a key from the capability panel and clears the plaintext field after submission', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/api-configs/IMAGE/llm.text.free/keys') && init?.method === 'POST') {
        return Response.json({ success: true, data: selectedKey('key-added'), error: null });
      }
      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({
          success: true,
          data: [{ ...selectedSlots()[0], keys: [selectedKey('key-added')] }],
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
    store.slotsByChain.IMAGE = [{ ...selectedSlots()[0], keys: [] }];

    expect(await screen.findByText('文本规划 LLM')).toBeInTheDocument();
    expect(screen.queryByLabelText('api key')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '添加 Key' }));

    expect(await screen.findByRole('dialog', { name: '添加 Key' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('provider'), 'fixture-free');
    await userEvent.type(screen.getByLabelText('key label'), 'fixture');
    await userEvent.type(screen.getByLabelText('model'), 'qwen-free');
    await userEvent.type(screen.getByLabelText('api key'), 'plain-secret-1234');
    await userEvent.click(screen.getByRole('button', { name: '提交 Key' }));

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '添加 Key' })).not.toBeInTheDocument());
    expect(screen.queryByLabelText('api key')).not.toBeInTheDocument();
    const requestBody = String(fetchMock.mock.calls[0][1]?.body);
    expect(requestBody).toContain('plain-secret-1234');
    expect(requestBody).toContain('"model":"qwen-free"');
    expect(store.slotsByChain.IMAGE[0].keys[0].maskedKey).toBe('****1234');
  });

  it('loads a workspace chain run and renders only the final image result in the dialog', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.includes('/api/chain-runs/chain-1/external-jobs')) {
        return Response.json({ success: true, data: externalJobFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1/api-selection-snapshot')) {
        return Response.json({ success: true, data: apiSelectionSnapshotsFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1')) {
        return Response.json({ success: true, data: chainRunFixture(), error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);

    renderApp('/workspace/chain-1');

    const finalImage = await screen.findByRole('img', { name: '生成图片结果' });
    expect(finalImage).toHaveAttribute('src', '/assets/final.svg');
    expect(screen.queryByText('链路已完成，最终产物和验收报告已固化。')).not.toBeInTheDocument();
    expect(screen.queryByText('I00')).not.toBeInTheDocument();
    expect(screen.queryByText('provider-job-abcd...7890')).not.toBeInTheDocument();
    expect(screen.queryByText('SUCCEEDED')).not.toBeInTheDocument();
    expect(screen.queryByText('FREE_PROVIDER_RETRY_ONLY')).not.toBeInTheDocument();
    expect(screen.queryByText('API 选择快照')).not.toBeInTheDocument();
    expect(screen.queryByText('llm.text.free')).not.toBeInTheDocument();
    expect(screen.queryByText('fixture-free')).not.toBeInTheDocument();
    expect(screen.queryByText('free-model')).not.toBeInTheDocument();
    expect(screen.queryByText('****1234')).not.toBeInTheDocument();
    expect(screen.queryByText('fixture-free-quota:available')).not.toBeInTheDocument();
    expect(screen.queryByText('provider-job-abcdef1234567890')).not.toBeInTheDocument();
    expect(screen.queryByText('最终图片')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('交付摘要')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('建议操作')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '查看资产库' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('结果操作')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '再生成' })).toBeInTheDocument();
    expect(screen.queryByLabelText('底部二次操作')).not.toBeInTheDocument();
  });

  it('renders only the final video result in the dialog', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.includes('/api/chain-runs/chain-video/external-jobs')) {
        return Response.json({ success: true, data: externalJobFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-video/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-video')) {
        return Response.json({ success: true, data: succeededVideoChainRunFixture(), error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/workspace/chain-video');

    const finalVideo = await screen.findByLabelText('生成视频结果');
    expect(finalVideo).toHaveAttribute('src', '/assets/final-video.mp4');
    expect(screen.queryByText('最终视频')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('交付摘要')).not.toBeInTheDocument();
    expect(screen.queryByText('候选摘要：完整短片 10 秒，画幅 9:16')).not.toBeInTheDocument();
    expect(screen.queryByText('人声验收：可听清人声，视频验收交付已按固定 rubric 通过'))
      .not.toBeInTheDocument();
    expect(screen.queryByText('Provider Job 摘要：provider-job-abcd...7890')).not.toBeInTheDocument();
    expect(screen.queryByText('provider-job-abcdef1234567890')).not.toBeInTheDocument();
  });

  it('edits the workspace user bubble inline and restarts through the chain-run API on confirm', async () => {
    const editedRun = {
      ...chainRunFixture(),
      chainRunId: 'chain-edited',
      userGoal: '生成一张赛博侦探短剧封面'
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/chain-runs/chain-1/external-jobs')) {
        return Response.json({ success: true, data: externalJobFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-1') && !init?.method) {
        return Response.json({ success: true, data: chainRunFixture(), error: null });
      }
      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({ success: true, data: selectedSlots(), error: null });
      }
      if (url.endsWith('/api/projects') && !init?.method) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.endsWith('/api/projects') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: {
            projectId: 'project-edited',
            title: '赛博侦探',
            goal: '生成一张赛博侦探短剧封面',
            createdAt: '2026-07-02T00:00:00Z'
          },
          error: null
        });
      }
      if (url.includes('/api/projects/project-edited/image-chain-runs') && init?.method === 'POST') {
        return Response.json({ success: true, data: editedRun, error: null });
      }
      if (url.includes('/api/chain-runs/chain-edited/external-jobs')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-edited/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-edited') && !init?.method) {
        return Response.json({ success: true, data: editedRun, error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/workspace/chain-1');

    expect(await screen.findAllByText('生成一张都市悬疑短剧封面')).not.toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: '调整目标' }));

    const editor = screen.getByLabelText('编辑创作目标');
    expect(editor).toHaveValue('生成一张都市悬疑短剧封面');

    await userEvent.click(screen.getByRole('button', { name: '取消' }));

    expect(screen.queryByLabelText('编辑创作目标')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '调整目标' }));
    await userEvent.clear(screen.getByLabelText('编辑创作目标'));
    await userEvent.type(screen.getByLabelText('编辑创作目标'), '生成一张赛博侦探短剧封面');
    await userEvent.click(screen.getByRole('button', { name: '确定' }));

    await waitFor(() => expect(window.location.pathname).toBe('/workspace/chain-edited'));
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/projects/project-edited/image-chain-runs'),
      expect.objectContaining({ method: 'POST' })
    );
    expect(fetchMock.mock.calls.map((call) => String(call[0])).join('\n')).not.toContain('pipeline-runs');
  });

  it('cancels an executing chain from the workspace action', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/chain-runs/chain-1/external-jobs')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-1/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-1:cancel') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: { ...executingChainRunFixture(), status: 'CANCELLED' },
          error: null
        });
      }
      if (url.includes('/api/chain-runs/chain-1') && !init?.method) {
        return Response.json({ success: true, data: executingChainRunFixture(), error: null });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/workspace/chain-1');

    await screen.findByText('正在生成中。');
    await userEvent.click(screen.getByRole('button', { name: '取消链路' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/chain-runs/chain-1:cancel'),
        expect.objectContaining({ method: 'POST' }));
    });
    expect(await screen.findByText('链路已取消，可重新生成。')).toBeInTheDocument();
  });

  it('redoes the generation stage from the workspace action instead of calling old providers', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/chain-runs/chain-1/external-jobs')) {
        return Response.json({ success: true, data: externalJobFixture(), error: null });
      }
      if (url.includes('/api/chain-runs/chain-1/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-1') && !init?.method) {
        return Response.json({ success: true, data: chainRunFixture(), error: null });
      }
      if (url.includes('/api/stage-runs/stage-2:redo') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: {
            ...chainRunFixture(),
            updatedAt: '2026-07-02T00:01:00Z',
            stageRuns: [
              ...chainRunFixture().stageRuns.slice(0, 1),
              {
                ...chainRunFixture().stageRuns[1],
                stageRunId: 'stage-2-redone',
                providerJobIds: ['provider-job-redone']
              },
              chainRunFixture().stageRuns[2]
            ]
          },
          error: null
        });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/workspace/chain-1');

    await screen.findByRole('img', { name: '生成图片结果' });
    await userEvent.click(screen.getByRole('button', { name: '再生成' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/stage-runs/stage-2:redo'),
        expect.objectContaining({ method: 'POST' }));
    });
    expect(fetchMock.mock.calls.map((call) => String(call[0])).join('\n')).not.toContain('pipeline-runs');
  });

  it('redoes the recovery waiting stage after provider reselection', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/chain-runs/chain-waiting/external-jobs')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-waiting/api-selection-snapshot')) {
        return Response.json({ success: true, data: [], error: null });
      }
      if (url.includes('/api/chain-runs/chain-waiting') && !init?.method) {
        return Response.json({ success: true, data: waitingUserChainRunFixture(), error: null });
      }
      if (url.includes('/api/stage-runs/stage-recovery-v30:redo') && init?.method === 'POST') {
        return Response.json({
          success: true,
          data: {
            ...waitingUserChainRunFixture(),
            status: 'SUCCEEDED',
            currentStageCode: 'V60',
            blockingReason: null,
            artifacts: chainRunFixture().artifacts
          },
          error: null
        });
      }
      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/workspace/chain-waiting');

    await screen.findByText('没有其他完整视频 provider，等待用户重新选择');
    await userEvent.click(screen.getByRole('button', { name: '再次生成' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/stage-runs/stage-recovery-v30:redo'),
        expect.objectContaining({ method: 'POST' }));
    });
    expect(fetchMock.mock.calls.map((call) => String(call[0])).join('\n'))
      .not.toContain('/api/stage-runs/stage-v40-original:redo');
  });

  it('renders only final media assets and opens the preview modal', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({ success: true, data: chainRunFixture().artifacts, error: null })
    ));

    render(AssetsPage);

    const card = await screen.findByRole('button', { name: /最终图片/ });
    expect(screen.queryByText('图片验收报告')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '报告' })).not.toBeInTheDocument();

    await userEvent.click(card);

    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('img', { name: '最终图片' }))
      .toHaveAttribute('src', '/assets/final.svg');

    await userEvent.click(within(dialog).getByRole('button', { name: '关闭预览' }));
  });

  it('supports sidebar collapse and keyboard resizing without layout text overflow', async () => {
    render(CreationSidebar, {
      global: {
        plugins: [createPinia(), createRouter({ history: createWebHistory(), routes })]
      }
    });

    await userEvent.tab();
    await userEvent.keyboard('{End}');

    expect(screen.getAllByText('对话')).toHaveLength(1);
    expect(screen.getByLabelText('对话')).toBeInTheDocument();
    expect(screen.queryByText('历史对话')).not.toBeInTheDocument();
    expect(screen.getByLabelText('调整侧边栏宽度')).toHaveAttribute('role', 'separator');
  });
});
