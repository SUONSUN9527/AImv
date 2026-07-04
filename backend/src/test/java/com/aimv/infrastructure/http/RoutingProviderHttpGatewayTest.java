package com.aimv.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.provider.ProviderSecretResolver;
import com.aimv.shared.error.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class RoutingProviderHttpGatewayTest {

    @Test
    void routesDashScopeTextCapabilityWithSelectedUserSecretWhenEnvironmentKeyIsBlank() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ProviderSecretResolver resolver = apiKeyId -> "key-1".equals(apiKeyId)
            ? Optional.of("selected-user-token-9876") : Optional.empty();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            DashScopeRagProviderOptions.disabled(), DashScopeVideoProviderOptions.disabled(), resolver);

        server.expect(once(), requestTo("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer selected-user-token-9876"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"qwen-plus\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("城市侦探")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("selected-user-token-9876"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("****9876"))))
            .andRespond(withSuccess("""
                {
                  "id": "chatcmpl-user-key-1234567890",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "用户 key 路由成功"
                      }
                    }
                  ],
                  "usage": {
                    "total_tokens": 9
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I10",
            "node-1", "TextPlanningAgent", "llm.text.free", "dashscope-free", "qwen-plus",
            "free-gate-1", "key-1", "****9876", Map.of("userGoal", "城市侦探")));

        assertThat(response.providerJobId()).isEqualTo("chatcmpl-user-key-1234567890");
        assertThat(response.outputSummary()).isEqualTo("用户 key 路由成功");
        server.verify();
    }

    @Test
    void routesDashScopeTextCapabilityToOpenAiCompatibleEndpointWithoutSecretInBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            new DashScopeTextProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/compatible-mode/v1", "qwen-plus"));

        server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer fixture-dashscope-token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"qwen-plus\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("城市侦探")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fixture-dashscope-token"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("****1234"))))
            .andRespond(withSuccess("""
                {
                  "id": "chatcmpl-test-1234567890",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "分镜规划完成"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 18,
                    "completion_tokens": 7,
                    "total_tokens": 25
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I10",
            "node-1", "TextPlanningAgent", "llm.text.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234", Map.of("userGoal", "城市侦探")));

        assertThat(response.providerJobId()).isEqualTo("chatcmpl-test-1234567890");
        assertThat(response.outputSummary()).isEqualTo("分镜规划完成");
        assertThat(response.freeQuotaSnapshot()).isEqualTo("dashscope-openai-compatible:quota-not-returned");
        assertThat(response.providerMetadata())
            .containsEntry("adapterKind", "DASHSCOPE_OPENAI_COMPATIBLE")
            .containsEntry("providerResponseId", "chatcmpl-test-1234567890");
        server.verify();
    }

    @Test
    void postsNonFixtureProviderToConfiguredHttpAdapterWithoutPlainSecret() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "http://adapter.test");

        server.expect(once(), requestTo("http://adapter.test/v1/provider-jobs"))
            .andExpect(method(POST))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"provider\":\"dashscope-free\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"apiKeyId\":\"key-1\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("plain-secret"))))
            .andRespond(withSuccess("""
                {
                  "providerJobId": "job-123",
                  "status": "SUCCEEDED",
                  "outputSummary": "adapter completed",
                  "artifactRefs": ["artifact-1"],
                  "providerMetadata": {"adapterKind": "HTTP_ADAPTER"},
                  "freeQuotaSnapshot": "free-quota-ok"
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I40",
            "node-1", "ImageGenerationAgent", "image.generate.free", "dashscope-free", "wanx-free",
            "free-gate-1", "key-1", "****1234", Map.of("prompt", "城市侦探")));

        assertThat(response.providerJobId()).isEqualTo("job-123");
        assertThat(response.freeQuotaSnapshot()).isEqualTo("free-quota-ok");
        server.verify();
    }

    @Test
    void routesDashScopeImageCapabilityToMultimodalEndpointWithoutSecretInBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(),
            new DashScopeImageProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/api/v1", "wan2.6-t2i", "720*1280"));

        server.expect(once(),
                requestTo("https://dashscope.test/api/v1/services/aigc/multimodal-generation/generation"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer fixture-dashscope-token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"wan2.6-t2i\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"size\":\"720*1280\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("城市侦探")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fixture-dashscope-token"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("****1234"))))
            .andRespond(withSuccess("""
                {
                  "request_id": "image-request-1234567890",
                  "output": {
                    "choices": [
                      {
                        "message": {
                          "content": [
                            {
                              "image": "https://dashscope-result.example/i40.png",
                              "type": "image"
                            }
                          ]
                        }
                      }
                    ],
                    "finished": true
                  },
                  "usage": {
                    "image_count": 1,
                    "size": "720*1280"
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I40",
            "node-1", "ImageGenerationAgent", "image.generate.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234", Map.of("prompt", "城市侦探封面")));

        assertThat(response.providerJobId()).isEqualTo("image-request-1234567890");
        assertThat(response.artifactRefs()).containsExactly("https://dashscope-result.example/i40.png");
        assertThat(response.freeQuotaSnapshot()).isEqualTo("dashscope-image-sync:quota-not-returned");
        assertThat(response.providerMetadata())
            .containsEntry("adapterKind", "DASHSCOPE_IMAGE_SYNC")
            .containsEntry("providerResponseId", "image-request-1234567890");
        server.verify();
    }

    @Test
    void routesDashScopeEmbeddingCapabilityToOpenAiCompatibleEndpointWithoutSecretInBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            new DashScopeRagProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/compatible-mode/v1", "text-embedding-v4", 1024,
                "qwen3-rerank", 2));

        server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/embeddings"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer fixture-dashscope-token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "\"model\":\"text-embedding-v4\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"dimensions\":1024")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "\"encoding_format\":\"float\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("城市侦探")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fixture-dashscope-token"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("****1234"))))
            .andRespond(withSuccess("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "embedding",
                      "index": 0,
                      "embedding": [0.1, 0.2, 0.3, 0.4]
                    }
                  ],
                  "model": "text-embedding-v4",
                  "usage": {
                    "prompt_tokens": 8,
                    "total_tokens": 8
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I20",
            "node-1", "RagEmbeddingAgent", "rag.embedding.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234", Map.of("userGoal", "城市侦探")));

        assertThat(response.providerJobId()).isEqualTo("dashscope-embedding-trace-1");
        assertThat(response.outputSummary()).isEqualTo(
            "DashScope embedding completed: 1 vector(s), dimension 4");
        assertThat(response.freeQuotaSnapshot()).isEqualTo("dashscope-embedding:quota-not-returned");
        assertThat(response.providerMetadata())
            .containsEntry("adapterKind", "DASHSCOPE_EMBEDDING_OPENAI_COMPATIBLE")
            .containsEntry("providerResponseId", "dashscope-embedding-trace-1")
            .containsEntry("embeddingCount", 1)
            .containsEntry("embeddingDimensions", 4);
        server.verify();
    }

    @Test
    void routesDashScopeRerankCapabilityToCompatibleEndpointWithoutSecretInBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            new DashScopeRagProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/compatible-mode/v1", "text-embedding-v4", 1024,
                "qwen3-rerank", 2));

        server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/reranks"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer fixture-dashscope-token"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "\"model\":\"qwen3-rerank\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"query\":\"城市侦探\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"top_n\":2")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("赛博都市")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fixture-dashscope-token"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("****1234"))))
            .andRespond(withSuccess("""
                {
                  "object": "list",
                  "results": [
                    {
                      "index": 0,
                      "relevance_score": 0.93
                    },
                    {
                      "index": 1,
                      "relevance_score": 0.31
                    }
                  ],
                  "model": "qwen3-rerank",
                  "id": "rerank-request-1234567890",
                  "usage": {
                    "total_tokens": 79
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I30",
            "node-1", "RagRerankAgent", "rag.rerank.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234", Map.of(
                "query", "城市侦探",
                "documents", List.of("赛博都市追踪", "轻喜剧办公室"))));

        assertThat(response.providerJobId()).isEqualTo("rerank-request-1234567890");
        assertThat(response.outputSummary()).isEqualTo("DashScope rerank completed: 2 result(s)");
        assertThat(response.freeQuotaSnapshot()).isEqualTo("dashscope-rerank:quota-not-returned");
        assertThat(response.providerMetadata())
            .containsEntry("adapterKind", "DASHSCOPE_RERANK_COMPATIBLE")
            .containsEntry("providerResponseId", "rerank-request-1234567890")
            .containsEntry("resultCount", 2);
        server.verify();
    }

    @Test
    void routesDashScopeVideoCapabilityToAsyncVideoEndpointWithoutSecretInBody() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            DashScopeRagProviderOptions.disabled(),
            new DashScopeVideoProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/api/v1", "wan2.7-t2v", "720P", "9:16", 10, 1,
                Duration.ZERO));

        server.expect(once(),
                requestTo("https://dashscope.test/api/v1/services/aigc/video-generation/video-synthesis"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer fixture-dashscope-token"))
            .andExpect(header("X-DashScope-Async", "enable"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"model\":\"wan2.7-t2v\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("城市侦探")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString(
                "\"audio_url\":\"https://media.example/voice.mp3\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"size\":\"720*1280\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("fixture-dashscope-token"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("****1234"))))
            .andRespond(withSuccess("""
                {
                  "request_id": "video-request-1234567890",
                  "output": {
                    "task_status": "PENDING",
                    "task_id": "videojob-1234567890"
                  }
                }
                """, MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://dashscope.test/api/v1/tasks/videojob-1234567890"))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer fixture-dashscope-token"))
            .andRespond(withSuccess("""
                {
                  "request_id": "video-poll-1234567890",
                  "output": {
                    "task_status": "SUCCEEDED",
                    "task_id": "videojob-1234567890",
                    "video_url": "https://dashscope-result.example/v40.mp4"
                  },
                  "usage": {
                    "duration": 10,
                    "size": "720*1280",
                    "video_count": 1
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "V40",
            "node-1", "FullVideoGenerationAgent", "video.generate.full_with_voice.free",
            "dashscope-free", "", "free-gate-1", "key-1", "****1234", Map.of(
                "userGoal", "城市侦探",
                "audioUrl", "https://media.example/voice.mp3")));

        assertThat(response.providerJobId()).isEqualTo("videojob-1234567890");
        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.artifactRefs()).containsExactly("https://dashscope-result.example/v40.mp4");
        assertThat(response.freeQuotaSnapshot()).isEqualTo("dashscope-video-async:quota-not-returned");
        assertThat(response.providerMetadata())
            .containsEntry("adapterKind", "DASHSCOPE_VIDEO_ASYNC")
            .containsEntry("providerTaskId", "videojob-1234567890")
            .containsEntry("audioDriven", true);
        server.verify();
    }

    @Test
    void dashScopeVideoVerifyDoesNotSubmitGenerationWhenNativeHumanVoiceIsUnsupported() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            DashScopeRagProviderOptions.disabled(),
            new DashScopeVideoProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/api/v1", "wan2.7-t2v", "720P", "9:16", 10, 1,
                Duration.ZERO));

        ProviderHttpResponse response = gateway.invoke(new ProviderHttpRequest("trace-verify",
            "verify-chain-1", "verify-stage-1", "VERIFY", "verify-node-1",
            "FreeModelGateVerify", "video.generate.full_with_voice.free", "dashscope-free",
            "wan2.7-t2v", "free-gate-1", "key-1", "****1234",
            Map.of("operation", "VERIFY_API_KEY", "freeOnly", true)));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.outputSummary()).contains("原生人声");
        assertThat(response.providerMetadata())
            .containsEntry("adapterKind", "DASHSCOPE_VIDEO_ASYNC")
            .containsEntry("completeShortVideoSupported", true)
            .containsEntry("nativeHumanVoiceSupported", false)
            .containsEntry("durationSeconds", 10)
            .containsEntry("aspectRatio", "9:16");
        server.verify();
    }

    @Test
    void refusesDashScopeTextProviderWhenNoDashScopeKeyAndNoAdapterConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "");

        ProviderHttpRequest request = new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I10",
            "node-1", "TextPlanningAgent", "llm.text.free", "dashscope-free", "qwen-plus",
            "free-gate-1", "key-1", "****1234", Map.of("userGoal", "城市侦探"));

        assertThatThrownBy(() -> gateway.invoke(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非 fixture provider 需要配置云端 HTTP adapter 地址");
    }

    @Test
    void refusesDashScopeImageProviderWhenNoDashScopeKeyAndNoAdapterConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "");

        ProviderHttpRequest request = new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I40",
            "node-1", "ImageGenerationAgent", "image.generate.free", "dashscope-free", "wan2.6-t2i",
            "free-gate-1", "key-1", "****1234", Map.of("prompt", "城市侦探封面"));

        assertThatThrownBy(() -> gateway.invoke(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非 fixture provider 需要配置云端 HTTP adapter 地址");
    }

    @Test
    void refusesDashScopeEmbeddingProviderWhenNoDashScopeKeyAndNoAdapterConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "");

        ProviderHttpRequest request = new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I20",
            "node-1", "RagEmbeddingAgent", "rag.embedding.free", "dashscope-free", "text-embedding-v4",
            "free-gate-1", "key-1", "****1234", Map.of("userGoal", "城市侦探"));

        assertThatThrownBy(() -> gateway.invoke(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非 fixture provider 需要配置云端 HTTP adapter 地址");
    }

    @Test
    void refusesDashScopeRerankProviderWhenNoDashScopeKeyAndNoAdapterConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "");

        ProviderHttpRequest request = new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I30",
            "node-1", "RagRerankAgent", "rag.rerank.free", "dashscope-free", "qwen3-rerank",
            "free-gate-1", "key-1", "****1234", Map.of("query", "城市侦探"));

        assertThatThrownBy(() -> gateway.invoke(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非 fixture provider 需要配置云端 HTTP adapter 地址");
    }

    @Test
    void refusesDashScopeVideoProviderWhenNoDashScopeKeyAndNoAdapterConfigured() {
        RestTemplate restTemplate = new RestTemplate();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "");

        ProviderHttpRequest request = new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "V40",
            "node-1", "FullVideoGenerationAgent", "video.generate.full_with_voice.free",
            "dashscope-free", "wan2.7-t2v", "free-gate-1", "key-1", "****1234",
            Map.of("userGoal", "城市侦探"));

        assertThatThrownBy(() -> gateway.invoke(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("非 fixture provider 需要配置云端 HTTP adapter 地址");
    }

    @Test
    void rejectsMalformedAdapterResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "http://adapter.test");

        server.expect(once(), requestTo("http://adapter.test/v1/provider-jobs"))
            .andExpect(method(POST))
            .andRespond(withSuccess("{\"status\":\"SUCCEEDED\"}", MediaType.APPLICATION_JSON));

        ProviderHttpRequest request = new ProviderHttpRequest("trace-1", "chain-1", "stage-1", "I40",
            "node-1", "ImageGenerationAgent", "image.generate.free", "dashscope-free", "wanx-free",
            "free-gate-1", "key-1", "****1234", Map.of());

        assertThatThrownBy(() -> gateway.invoke(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("providerJobId");
        server.verify();
    }

    @Test
    void parsesDashScopeTextStructuredJsonIntoPartialOutputWhenPartialSchemaProvided() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            new DashScopeTextProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/compatible-mode/v1", "qwen-plus"));

        server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {
                  "id": "chatcmpl-structured-1",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "```json\\n{\\"subject\\":\\"侦探背影\\",\\"scene\\":\\"雨夜街口\\",\\"style\\":\\"neon\\",\\"aspectRatio\\":\\"9:16\\",\\"count\\":1,\\"goalClarityScore\\":95,\\"safetyScore\\":100}\\n```"
                      }
                    }
                  ]
                }
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-2", "chain-1", "stage-1", "I00",
            "node-1", "GoalAgent", "llm.text.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234",
            Map.of("userGoal", "生成一张9:16都市悬疑封面", "partialSchema",
                Map.of("required", List.of("subject", "scene", "style", "aspectRatio", "count",
                    "goalClarityScore", "safetyScore"),
                    "allowed", List.of("subject", "scene", "style", "aspectRatio", "count",
                        "goalClarityScore", "safetyScore")))));

        assertThat(response.providerJobId()).isEqualTo("chatcmpl-structured-1");
        assertThat(response.providerMetadata()).containsKey("partialOutput");
        Object partialOutput = response.providerMetadata().get("partialOutput");
        assertThat(partialOutput).isInstanceOf(Map.class);
        assertThat(castStringMap(partialOutput))
            .containsEntry("subject", "侦探背影")
            .containsEntry("aspectRatio", "9:16")
            .containsEntry("count", 1)
            .containsEntry("safetyScore", 100);
        server.verify();
    }

    @Test
    void retriesDashScopeTextStructuredOutputWhenFirstAttemptIsNotValidJson() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            new DashScopeTextProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/compatible-mode/v1", "qwen-plus"));

        server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {"id":"c-1","choices":[{"message":{"content":"抱歉，我先解释一下我的思路……"}}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
            .andExpect(method(POST))
            .andRespond(withSuccess("""
                {"id":"c-2","choices":[{"message":{"content":"{\\"safetyPassed\\":true}"}}]}
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-3", "chain-1", "stage-1", "I20",
            "node-1", "PromptSafetyAgent", "llm.text.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234",
            Map.of("userGoal", "生成封面", "partialSchema",
                Map.of("required", List.of("safetyPassed"), "allowed", List.of("safetyPassed", "vetoReason")))));

        assertThat(response.providerMetadata()).containsKey("partialOutput");
        assertThat(castStringMap(response.providerMetadata().get("partialOutput")))
            .containsEntry("safetyPassed", true);
        assertThat(response.providerMetadata()).containsEntry("structuredOutputAttempts", 2);
        server.verify();
    }

    @Test
    void reportsParseErrorWhenStructuredOutputMissesRequiredFieldAfterRetries() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            new DashScopeTextProviderOptions("fixture-dashscope-token",
                "https://dashscope.test/compatible-mode/v1", "qwen-plus"));

        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(once(), requestTo("https://dashscope.test/compatible-mode/v1/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                    {"id":"c","choices":[{"message":{"content":"{\\"scene\\":\\"街口\\"}"}}]}
                    """, MediaType.APPLICATION_JSON));
        }

        var response = gateway.invoke(new ProviderHttpRequest("trace-4", "chain-1", "stage-1", "I00",
            "node-1", "GoalAgent", "llm.text.free", "dashscope-free", "",
            "free-gate-1", "key-1", "****1234",
            Map.of("userGoal", "生成封面", "partialSchema",
                Map.of("required", List.of("subject", "scene"), "allowed", List.of("subject", "scene")))));

        assertThat(response.providerMetadata()).doesNotContainKey("partialOutput");
        assertThat(response.providerMetadata()).containsKey("parseError");
        assertThat(String.valueOf(response.providerMetadata().get("parseError"))).contains("subject");
        server.verify();
    }

    @Test
    void routesPollinationsTextCapabilityToOpenAiCompatibleEndpointAndParsesStructuredOutput() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            DashScopeRagProviderOptions.disabled(), DashScopeVideoProviderOptions.disabled(),
            ProviderSecretResolver.empty(),
            new PollinationsProviderOptions("", "https://text.test", "openai",
                "https://image.test", "flux", 720, 1280));

        server.expect(once(), requestTo("https://text.test/openai"))
            .andExpect(method(POST))
            .andExpect(content().string(containsString("侦探背影")))
            .andRespond(withSuccess("""
                {"id":"pllns-1","choices":[{"message":{"content":
                "{\\"safetyPassed\\":true}"}}]}
                """, MediaType.APPLICATION_JSON));

        var response = gateway.invoke(new ProviderHttpRequest("trace-p1", "chain-1", "stage-1", "I20",
            "node-1", "PromptSafetyAgent", "llm.text.free", "pollinations-free", "free-model",
            "gate-1", "key-1", "****1234",
            Map.of("userGoal", "侦探背影 都市悬疑", "partialSchema",
                Map.of("required", List.of("safetyPassed"), "allowed", List.of("safetyPassed")))));

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(castStringMap(response.providerMetadata().get("partialOutput")))
            .containsEntry("safetyPassed", true);
        assertThat(response.freeQuotaSnapshot()).isEqualTo("pollinations-free:anonymous-tier");
        server.verify();
    }

    @Test
    void routesPollinationsImageCapabilityToPromptEndpointAndReturnsRealImageArtifact() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        RoutingProviderHttpGateway gateway = new RoutingProviderHttpGateway(restTemplate, "",
            DashScopeTextProviderOptions.disabled(), DashScopeImageProviderOptions.disabled(),
            DashScopeRagProviderOptions.disabled(), DashScopeVideoProviderOptions.disabled(),
            ProviderSecretResolver.empty(),
            new PollinationsProviderOptions("", "https://text.test", "openai",
                "https://image.test", "flux", 720, 1280));

        byte[] jpeg = new byte[4096];
        server.expect(once(), requestTo(containsString("https://image.test/prompt/")))
            .andExpect(method(GET))
            .andRespond(withSuccess(jpeg, MediaType.IMAGE_JPEG));

        var response = gateway.invoke(new ProviderHttpRequest("trace-p2", "chain-1", "stage-1", "I40",
            "node-1", "ImageGenerationAgent", "image.generate.free", "pollinations-free", "flux",
            "gate-1", "key-1", "****1234", Map.of("prompt", "neon suspense detective poster 9:16")));

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.artifactRefs()).hasSize(1);
        assertThat(response.artifactRefs().get(0)).contains("https://image.test/prompt/");
        assertThat(response.providerMetadata())
            .containsEntry("candidateCount", 1)
            .containsEntry("aspectRatio", "9:16")
            .containsEntry("artifactIntegrityScore", 100);
        server.verify();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castStringMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
