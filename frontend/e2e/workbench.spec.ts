import { expect, test } from '@playwright/test';

const missingImageSlots = [
  { chainType: 'IMAGE', capabilityType: 'llm.text.free', label: '文本规划 LLM', required: true, keys: [] },
  { chainType: 'IMAGE', capabilityType: 'rag.embedding.free', label: 'RAG Embedding', required: true, keys: [] },
  { chainType: 'IMAGE', capabilityType: 'rag.rerank.free', label: 'RAG Rerank', required: true, keys: [] },
  { chainType: 'IMAGE', capabilityType: 'image.generate.free', label: '图片生成', required: true, keys: [] }
];

const videoArtifacts = [
  {
    artifactId: 'video-1',
    chainRunId: 'chain-1',
    chainType: 'VIDEO',
    artifactKind: 'FinalVideoArtifact',
    displayName: '最终视频',
    url: '/assets/final-video.mp4',
    contentHash: 'sha256-fixture',
    metadata: {},
    createdAt: '2026-07-02T00:00:00Z'
  }
];

test.describe('AImv Vue3 workbench acceptance', () => {
  test('desktop generate flow blocks missing free capability config', async ({ page }) => {
    await page.route('**/api/api-configs?chainType=IMAGE', async (route) => {
      await route.fulfill({ json: { success: true, data: missingImageSlots, error: null } });
    });
    await page.route('**/api/projects', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: { success: true, data: [], error: null } });
        return;
      }
      throw new Error(`generation should be blocked before ${route.request().url()}`);
    });

    await page.goto('/generate');

    await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible();
    await expect(page.getByRole('button', { name: '图片生成' })).toBeVisible();
    await expect(page.getByRole('button', { name: '视频生成' })).toBeVisible();
    await expect(page.getByText('音乐生成')).toHaveCount(0);
    await expect(page.getByText('音频生成')).toHaveCount(0);

    await page.getByLabel('创作目标').fill('生成一张都市悬疑短剧封面');
    await page.getByRole('button', { name: '启动生成' }).click();

    await expect(page.getByRole('heading', { name: '能力配置' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '图片生成' })).toBeVisible();
    await expect(page.getByText('完整视频生成')).toHaveCount(0);
    await expect(page.getByLabel('api key')).toHaveCount(0);
    await page.getByRole('button', { name: '添加 Key' }).first().click();
    await expect(page.getByRole('dialog', { name: '添加 Key' })).toBeVisible();
  });

  test('responsive generate shell matches desktop and mobile layout', async ({ page }, testInfo) => {
    await page.goto('/generate');

    const railBox = await page.getByRole('navigation', { name: '主导航' }).boundingBox();
    expect(railBox).not.toBeNull();
    if (testInfo.project.name.includes('mobile')) {
      expect(railBox?.y).toBeLessThan(12);
      await expect(page.getByText('开启创作')).toBeHidden();
    } else {
      await expect(page.getByText('开启创作')).toBeVisible();
    }
    await expect(page.getByLabel('创作目标')).toBeVisible();
  });

  test('compact generate layout keeps the composer controls readable', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 900 });
    await page.goto('/generate');

    const imageModeBox = await page.getByRole('button', { name: '图片生成' }).boundingBox();
    const videoModeBox = await page.getByRole('button', { name: '视频生成' }).boundingBox();
    const imageModeLabelBox = await page.getByRole('button', { name: '图片生成' }).locator('span').boundingBox();
    const videoModeLabelBox = await page.getByRole('button', { name: '视频生成' }).locator('span').boundingBox();

    expect(imageModeBox?.height).toBeLessThanOrEqual(38);
    expect(videoModeBox?.height).toBeLessThanOrEqual(38);
    expect(imageModeLabelBox?.height).toBeLessThanOrEqual(22);
    expect(videoModeLabelBox?.height).toBeLessThanOrEqual(22);

    await page.setViewportSize({ width: 1024, height: 900 });
    await page.reload();

    const railBox = await page.getByRole('navigation', { name: '主导航' }).boundingBox();
    expect(railBox?.y).toBeLessThan(12);
    await expect(page.getByText('开启创作')).toBeHidden();
  });

  test('assets preview keeps complete-video playback speed controls', async ({ page }) => {
    await page.route('**/api/artifacts', async (route) => {
      await route.fulfill({ json: { success: true, data: videoArtifacts, error: null } });
    });

    await page.goto('/assets');
    await page.getByRole('button', { name: /最终视频/ }).click();

    const preview = page.getByLabel('最终视频预览');
    await expect(preview).toHaveAttribute('src', '/assets/final-video.mp4');
    await page.getByRole('button', { name: '1.5x' }).click();
    await expect(preview).toHaveJSProperty('playbackRate', 1.5);
  });
});
