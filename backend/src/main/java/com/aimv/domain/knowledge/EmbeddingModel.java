package com.aimv.domain.knowledge;

import com.aimv.domain.shared.ChainType;

/**
 * 文本向量化端口。实现优先走用户为该链路选中的云端免费额度 embedding（如 DashScope
 * text-embedding / Cloudflare bge-m3），未配置或不可用时回退到确定性本地向量，
 * 保证 RAG 在离线/测试环境仍可语义检索。领域层只依赖此端口，不感知 HTTP 或 provider。
 */
public interface EmbeddingModel {

    float[] embed(ChainType chainType, String text);

    String modelName(ChainType chainType);
}
