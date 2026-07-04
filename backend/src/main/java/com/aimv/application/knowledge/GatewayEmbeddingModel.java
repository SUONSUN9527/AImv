package com.aimv.application.knowledge;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.knowledge.DeterministicEmbedding;
import com.aimv.domain.knowledge.EmbeddingModel;
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
 * EmbeddingModel 的落地实现。优先用该链路当前选中的 rag.embedding.free 免费额度 provider
 * 计算向量（denseVector 从 provider 响应 metadata 取回），未配置、被拒或响应缺向量时
 * 回退到确定性本地向量，保证 RAG 在离线/测试环境仍可用。
 */
@Service
public class GatewayEmbeddingModel implements EmbeddingModel {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayEmbeddingModel.class);
    private static final String EMBEDDING_CAPABILITY = "rag.embedding.free";
    private static final String EMBEDDING_NODE = "RagEmbeddingNode";
    private static final String SUCCEEDED = "SUCCEEDED";

    private final ApiConfigRepository apiConfigRepository;
    private final ProviderHttpGateway providerHttpGateway;

    public GatewayEmbeddingModel(ApiConfigRepository apiConfigRepository,
            ProviderHttpGateway providerHttpGateway) {
        this.apiConfigRepository = apiConfigRepository;
        this.providerHttpGateway = providerHttpGateway;
    }

    @Override
    public float[] embed(ChainType chainType, String text) {
        Optional<ApiCredential> credential = embeddingCredential(chainType);
        if (credential.isEmpty()) {
            return DeterministicEmbedding.embed(text);
        }
        return remoteEmbed(chainType, text, credential.get())
            .orElseGet(() -> DeterministicEmbedding.embed(text));
    }

    @Override
    public String modelName(ChainType chainType) {
        return embeddingCredential(chainType)
            .map(ApiCredential::model)
            .filter(model -> model != null && !model.isBlank())
            .orElse(DeterministicEmbedding.MODEL_NAME);
    }

    private Optional<ApiCredential> embeddingCredential(ChainType chainType) {
        return apiConfigRepository.findSelectedCredentials(chainType).stream()
            .filter(credential -> EMBEDDING_CAPABILITY.equals(credential.capabilityType()))
            .findFirst();
    }

    private Optional<float[]> remoteEmbed(ChainType chainType, String text, ApiCredential credential) {
        try {
            ProviderHttpRequest request = new ProviderHttpRequest("trace-" + UUID.randomUUID(),
                "embedding-" + chainType.name().toLowerCase(), "embedding-stage", "EMBEDDING",
                "node-" + UUID.randomUUID(), EMBEDDING_NODE, EMBEDDING_CAPABILITY, credential.provider(),
                credential.model(), "gate-" + UUID.randomUUID(), credential.apiKeyId(), credential.maskedKey(),
                Map.of("text", text));
            ProviderHttpResponse response = providerHttpGateway.invoke(request);
            if (!SUCCEEDED.equals(response.status())) {
                return Optional.empty();
            }
            return denseVector(response.providerMetadata());
        } catch (RuntimeException exception) {
            LOGGER.warn("远程 embedding 调用失败，回退确定性向量: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<float[]> denseVector(Map<String, Object> metadata) {
        if (metadata == null || !(metadata.get("denseVector") instanceof List<?> values) || values.isEmpty()) {
            return Optional.empty();
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Number number)) {
                return Optional.empty();
            }
            vector[index] = number.floatValue();
        }
        return Optional.of(vector);
    }
}
