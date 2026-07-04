package com.aimv.interfaces.chain;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.provider.ProviderHttpGateway;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.shared.ChainType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
class ChainRunProviderFailureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    @MockitoBean
    private ProviderHttpGateway providerHttpGateway;

    @Test
    void returnsWaitingCapabilityWhenProviderReportsFreeQuotaExhausted() throws Exception {
        saveSelectedCredential("llm.text.free");
        saveSelectedCredential("rag.embedding.free");
        saveSelectedCredential("rag.rerank.free");
        saveSelectedCredential("image.generate.free");
        when(providerHttpGateway.invoke(any())).thenReturn(new ProviderHttpResponse("provider-job-quota",
            "FAILED", "free quota exhausted", List.of(), Map.of("rawErrorCode", "FREE_QUOTA_EXHAUSTED"),
            "free-quota-exhausted"));
        String projectId = createProject();

        mockMvc.perform(post("/api/projects/{projectId}/image-chain-runs", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userGoal\":\"生成一张都市悬疑短剧封面\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WAITING_CAPABILITY"))
            .andExpect(jsonPath("$.data.currentStageCode").value("I00"))
            .andExpect(jsonPath("$.data.blockingReason").value(containsString("free quota exhausted")))
            .andExpect(jsonPath("$.data.stageRuns.length()").value(1))
            .andExpect(jsonPath("$.data.stageRuns[0].status").value("WAITING_CAPABILITY"))
            .andExpect(jsonPath("$.data.stageRuns[0].reviewReport.passed").value(false))
            .andExpect(jsonPath("$.data.artifacts.length()").value(0))
            .andExpect(content().string(not(containsString("direct-secret-1234"))));
    }

    private String createProject() throws Exception {
        String response = mockMvc.perform(post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"都市悬疑\",\"goal\":\"生成短剧资产\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return JsonTestSupport.extractString(response, "projectId");
    }

    private void saveSelectedCredential(String capabilityType) {
        apiConfigRepository.save(new ApiCredential("direct-" + capabilityType, ChainType.IMAGE, capabilityType,
            "quota-provider", "direct", "hash", "encrypted", "****1234", "fixture-contract",
            ApiKeyStatus.ACTIVE, true, Instant.now(), FreeModelGateStatus.PASSED));
    }
}
