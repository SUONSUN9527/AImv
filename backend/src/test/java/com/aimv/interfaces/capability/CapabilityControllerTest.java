package com.aimv.interfaces.capability;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CapabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsRegisteredCapabilitiesWithoutSecretsOrOldAudioEditAbilities() throws Exception {
        mockMvc.perform(get("/api/capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].capabilityType",
                hasItems("llm.text.free", "image.generate.free", "video.generate.full_with_voice.free")))
            .andExpect(content().string(not(containsString("apiKey"))))
            .andExpect(content().string(not(containsString("lip"))))
            .andExpect(content().string(not(containsString("export-jobs"))));
    }

    @Test
    void discoversOnlyStageCapabilitiesInsideRequestedChain() throws Exception {
        mockMvc.perform(post("/api/capabilities:discover")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chainType\":\"IMAGE\",\"stageCode\":\"I40\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chainType").value("IMAGE"))
            .andExpect(jsonPath("$.data.stageCode").value("I40"))
            .andExpect(jsonPath("$.data.requiredCapabilities[*].capabilityType",
                hasItems("llm.text.free", "rag.embedding.free", "rag.rerank.free", "image.generate.free")))
            .andExpect(content().string(not(containsString("video.generate.full_with_voice.free"))));
    }

    @Test
    void rejectsAcquiringLocalModelWeights() throws Exception {
        mockMvc.perform(post("/api/capabilities:acquire")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"capabilityType\":\"llm.text.free\",\"downloadModelWeights\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("LOCAL_MODEL_DOWNLOAD_REJECTED"));
    }
}
