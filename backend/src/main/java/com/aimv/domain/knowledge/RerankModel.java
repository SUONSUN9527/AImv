package com.aimv.domain.knowledge;

import com.aimv.domain.shared.ChainType;
import java.util.List;

/**
 * 重排端口。技术文档 11.3 第 5 步：把 hybrid 融合后的候选交给云端免费额度 rerank 模型
 * （如 bge-m3 query/contexts）打相关性分。返回与 documents 同序、等长的分数列表；
 * 未配置 rerank key 或调用失败时返回空列表，检索保持 RRF 融合顺序（优雅降级）。
 */
public interface RerankModel {

    List<Double> rerank(ChainType chainType, String query, List<String> documents);
}
