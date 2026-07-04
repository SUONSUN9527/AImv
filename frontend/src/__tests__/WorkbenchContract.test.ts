import { render, screen } from '@testing-library/vue';
import userEvent from '@testing-library/user-event';
import { createPinia } from 'pinia';
import { createRouter, createWebHistory } from 'vue-router';
import { afterEach } from 'vitest';
import App from '../App.vue';
import { routes } from '../router/routes';

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderApp(path = '/generate') {
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

describe('Vue3 workbench contract', () => {
  it('keeps the documented Jimeng shell and only exposes image/video chain modes', async () => {
    vi.stubGlobal('fetch', vi.fn(async () =>
      Response.json({ success: true, data: [], error: null })
    ));
    renderApp('/generate');

    expect(await screen.findByRole('heading', { name: '开启你的movie生涯！' }))
      .toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: '主导航' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '图片生成' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '视频生成' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /能力/ })).toHaveAttribute('href', '/capability');
    expect(screen.getByRole('link', { name: '新对话' })).toHaveAttribute('href', '/generate');
    expect(screen.queryByText('音乐生成')).not.toBeInTheDocument();
    expect(screen.queryByText('音频生成')).not.toBeInTheDocument();
    expect(screen.queryByText('剪辑')).not.toBeInTheDocument();
    expect(screen.queryByText('灵感')).not.toBeInTheDocument();
    expect(screen.queryByText('画布')).not.toBeInTheDocument();
    expect(screen.queryByText('自动')).not.toBeInTheDocument();
    expect(screen.queryByText('使用技能')).not.toBeInTheDocument();
  });

  it('blocks generation with capability configuration instead of calling old pipeline APIs', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);

      if (url.includes('/api/api-configs?chainType=IMAGE')) {
        return Response.json({
          success: true,
          data: [
            {
              chainType: 'IMAGE',
              capabilityType: 'llm.text.free',
              label: '文本规划 LLM',
              required: true,
              keys: []
            },
            {
              chainType: 'IMAGE',
              capabilityType: 'rag.embedding.free',
              label: 'RAG Embedding',
              required: true,
              keys: []
            },
            {
              chainType: 'IMAGE',
              capabilityType: 'rag.rerank.free',
              label: 'RAG Rerank',
              required: true,
              keys: []
            },
            {
              chainType: 'IMAGE',
              capabilityType: 'image.generate.free',
              label: '图片生成',
              required: true,
              keys: []
            }
          ]
        });
      }
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
      if (url.endsWith('/api/projects') && !init?.method) {
        return Response.json({ success: true, data: [], error: null });
      }

      throw new Error(`unexpected request ${url}`);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderApp('/generate');

    await userEvent.type(await screen.findByLabelText('创作目标'), '生成一张都市悬疑短剧封面');
    await userEvent.click(screen.getByRole('button', { name: '启动生成' }));

    expect(await screen.findByRole('heading', { name: '能力配置' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/capability');
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('pipeline-runs'), expect.anything());
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('export-jobs'), expect.anything());
  });

  it('keeps edit route unsupported because the backend document forbids clipping', async () => {
    renderApp('/edit');

    expect(await screen.findByText('当前后端链路不支持剪辑')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '导出成片' })).not.toBeInTheDocument();
  });
});
