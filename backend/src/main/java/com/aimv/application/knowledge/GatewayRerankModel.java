package com.aimv.application.knowledge;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.knowledge.RerankModel;
import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * RerankModel 的落地实现。用该链路选中的 rag.rerank.free 免费额度 provider 对候选文档打分
 * （rerankScores 从 provider 响应 metadata 按 documents 同序取回）。未配置、被拒或分数缺失时
 * 返回空列表，检索保持 hybrid 融合顺序（优雅降级）。
 */
@Service
public class GatewayRerankModel implements RerankModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRerankModel.class);
    private static final String RERANK_CAPABILITY = "rag.rerank.free";
    private static final String RERANK_NODE = "RagRerankNode";
    private static final String SUCCEEDED = "SUCCEEDED";

    private final ApiConfigRepository apiConfigRepository;
    private final ProviderHttpGateway providerHttpGateway;

    public GatewayRerankModel(ApiConfigRepository apiConfigRepository,
            ProviderHttpGateway providerHttpGateway) {
        this.apiConfigRepository = apiConfigRepository;
        this.providerHttpGateway = providerHttpGateway;
    }

    @Override
    public List<Double> rerank(ChainType chainType, String query, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        Optional<ApiCredential> credential = rerankCredential(chainType);
        if (credential.isEmpty()) {
            return List.of();
        }
        try {
            ProviderHttpRequest request = new ProviderHttpRequest("trace-" + UUID.randomUUID(),
                "rerank-" + chainType.name().toLowerCase(), "rerank-stage", "RERANK",
                "node-" + UUID.randomUUID(), RERANK_NODE, RERANK_CAPABILITY, credential.get().provider(),
                credential.get().model(), "gate-" + UUID.randomUUID(), credential.get().apiKeyId(),
                credential.get().maskedKey(), Map.of("query", query, "documents", List.copyOf(documents)));
            ProviderHttpResponse response = providerHttpGateway.invoke(request);
            if (!SUCCEEDED.equals(response.status())) {
                return List.of();
            }
            return rerankScores(response.providerMetadata(), documents.size());
        } catch (RuntimeException exception) {
            LOGGER.warn("远程 rerank 调用失败，保持融合顺序: {}", exception.getMessage());
            return List.of();
        }
    }

    private Optional<ApiCredential> rerankCredential(ChainType chainType) {
        return apiConfigRepository.findSelectedCredentials(chainType).stream()
            .filter(credential -> RERANK_CAPABILITY.equals(credential.capabilityType()))
            .findFirst();
    }

    private List<Double> rerankScores(Map<String, Object> metadata, int expectedSize) {
        if (metadata == null || !(metadata.get("rerankScores") instanceof List<?> values)
                || values.size() != expectedSize) {
            return List.of();
        }
        List<Double> scores = new java.util.ArrayList<>(values.size());
        for (Object value : values) {
            if (!(value instanceof Number number)) {
                return List.of();
            }
            scores.add(number.doubleValue());
        }
        return List.copyOf(scores);
    }
}
