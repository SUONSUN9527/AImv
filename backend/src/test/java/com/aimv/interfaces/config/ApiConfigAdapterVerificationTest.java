package com.aimv.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.interfaces.chain.JsonTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiConfigAdapterVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderHttpGateway providerHttpGateway;

    @Test
    void verifiesNonFixtureKeyThroughHttpAdapterWithoutLeakingPlaintext() throws Exception {
        when(providerHttpGateway.invoke(any())).thenReturn(new ProviderHttpResponse("verify-job-1", "SUCCEEDED",
            "free quota verified", List.of(), Map.of("adapterKind", "HTTP_ADAPTER"), "free-quota-ok"));

        String response = mockMvc.perform(post("/api/api-configs/IMAGE/llm.text.free/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"dashscope-free\",\"label\":\"dashscope\","
                    + "\"apiKey\":\"plain-secret-9876\",\"model\":\"qwen-free\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.maskedKey").value("****9876"))
            .andExpect(content().string(not(containsString("plain-secret-9876"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String apiKeyId = JsonTestSupport.extractString(response, "apiKeyId");

        mockMvc.perform(post("/api/api-keys/{apiKeyId}:verify", apiKeyId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.freeModelGateStatus").value("PASSED"))
            .andExpect(content().string(not(containsString("plain-secret-9876"))));

        ArgumentCaptor<ProviderHttpRequest> requestCaptor = ArgumentCaptor.forClass(ProviderHttpRequest.class);
        verify(providerHttpGateway).invoke(requestCaptor.capture());
        ProviderHttpRequest adapterRequest = requestCaptor.getValue();

        assertThat(adapterRequest.provider()).isEqualTo("dashscope-free");
        assertThat(adapterRequest.model()).isEqualTo("qwen-free");
        assertThat(adapterRequest.apiKeyId()).isEqualTo(apiKeyId);
        assertThat(adapterRequest.maskedKey()).isEqualTo("****9876");
        assertThat(adapterRequest.toString()).doesNotContain("plain-secret-9876");
    }

    @Test
    void rejectsVideoKeyVerificationWhenProviderDoesNotProveNativeHumanVoiceCapability() throws Exception {
        when(providerHttpGateway.invoke(any())).thenReturn(new ProviderHttpResponse("verify-job-video",
            "SUCCEEDED", "video provider checked", List.of(), Map.of(
                "adapterKind", "DASHSCOPE_VIDEO_ASYNC",
                "completeShortVideoSupported", true,
                "nativeHumanVoiceSupported", false,
                "durationSeconds", 10,
                "aspectRatio", "9:16"
            ), "free-quota-ok"));

        String response = mockMvc.perform(post("/api/api-configs/VIDEO/video.generate.full_with_voice.free/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"dashscope-free\",\"label\":\"dashscope\","
                    + "\"apiKey\":\"plain-secret-9876\",\"model\":\"wan2.7-t2v\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("plain-secret-9876"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String apiKeyId = JsonTestSupport.extractString(response, "apiKeyId");

        mockMvc.perform(post("/api/api-keys/{apiKeyId}:verify", apiKeyId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("FREE_MODEL_GATE_FAILED"))
            .andExpect(content().string(containsString("原生人声")))
            .andExpect(content().string(not(containsString("plain-secret-9876"))));
    }
}
