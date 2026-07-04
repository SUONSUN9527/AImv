package com.aimv.interfaces.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class ApiConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsOnlyImageCapabilitySlotsForImageChain() throws Exception {
        mockMvc.perform(get("/api/api-configs?chainType=IMAGE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(4)))
            .andExpect(jsonPath("$.data[0].capabilityType").value("llm.text.free"))
            .andExpect(jsonPath("$.data[3].capabilityType").value("image.generate.free"))
            .andExpect(content().string(not(containsString("video.generate.full_with_voice.free"))))
            .andExpect(content().string(not(containsString("audio"))));
    }

    @Test
    void rejectsCapabilityConfiguredForWrongChain() throws Exception {
        mockMvc.perform(post("/api/api-configs/IMAGE/video.generate.full_with_voice.free/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"fixture-free\",\"label\":\"fixture\",\"apiKey\":\"secret-key-1234\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("CHAIN_CAPABILITY_MISMATCH"));
    }

    @Test
    void rejectsOverlongApiKeyPayload() throws Exception {
        String oversizedApiKey = "x".repeat(4097);

        mockMvc.perform(post("/api/api-configs/IMAGE/llm.text.free/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"fixture-free\",\"label\":\"fixture\",\"apiKey\":\""
                    + oversizedApiKey + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void neverReturnsPlainApiKeyAndRejectsDeletingSelectedOnlyKey() throws Exception {
        String response = mockMvc.perform(post("/api/api-configs/VIDEO/llm.text.free/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"fixture-free\",\"label\":\"fixture\",\"apiKey\":\"plain-secret-9876\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chainType").value("VIDEO"))
            .andExpect(jsonPath("$.data.capabilityType").value("llm.text.free"))
            .andExpect(jsonPath("$.data.maskedKey").value("****9876"))
            .andExpect(content().string(not(containsString("plain-secret-9876"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String apiKeyId = extractApiKeyId(response);

        mockMvc.perform(post("/api/api-keys/{apiKeyId}:verify", apiKeyId))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/api-keys/{apiKeyId}:select", apiKeyId))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/api-keys/{apiKeyId}", apiKeyId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DELETE_SELECTED_KEY_REJECTED"));
    }

    private String extractApiKeyId(String json) {
        int field = json.indexOf("\"apiKeyId\":\"");
        if (field < 0) {
            throw new AssertionError("apiKeyId missing in response: " + json);
        }
        int start = field + "\"apiKeyId\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
