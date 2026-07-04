package com.aimv.interfaces.knowledge;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aimv.interfaces.chain.JsonTestSupport;
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
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ingestsAndRetrievesOnlyMatchingNamespaceAndStageEvidence() throws Exception {
        String imageNamespace = "project:project-1:chain:chain-image-1";
        String videoNamespace = "project:project-1:chain:chain-video-1";
        ingest(imageNamespace, "IMAGE", "I20", "城市侦探, 9:16, 冷色调");
        ingest(videoNamespace, "VIDEO", "V20", "都市短剧, 10秒, 人声配音");

        String response = mockMvc.perform(post("/api/knowledge:retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + imageNamespace + "\",\"chainType\":\"IMAGE\",\"stageCode\":\"I20\","
                    + "\"query\":\"侦探\",\"topK\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.namespace").value(imageNamespace))
            .andExpect(jsonPath("$.data.stageCode").value("I20"))
            .andExpect(jsonPath("$.data.chunks[*].content", hasItems(containsString("城市侦探"))))
            .andExpect(content().string(not(containsString("人声配音"))))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String retrievalRecordId = JsonTestSupport.extractString(response, "retrievalRecordId");

        mockMvc.perform(get("/api/retrieval-records/{retrievalRecordId}", retrievalRecordId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.retrievalRecordId").value(retrievalRecordId))
            .andExpect(jsonPath("$.data.hitChunkIds.length()").value(1));
    }

    @Test
    void rejectsCrossChainNamespaceAccess() throws Exception {
        String videoNamespace = "project:project-1:chain:chain-video-1";
        ingest(videoNamespace, "VIDEO", "V20", "都市短剧, 10秒, 人声配音");

        mockMvc.perform(post("/api/knowledge:retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + videoNamespace + "\",\"chainType\":\"IMAGE\",\"stageCode\":\"I20\","
                    + "\"query\":\"测试\",\"topK\":3}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RAG_NAMESPACE_MISMATCH"));
    }

    @Test
    void rejectsLegacyOrMalformedPrivateNamespace() throws Exception {
        mockMvc.perform(post("/api/knowledge:ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"chain-image-1\",\"chainType\":\"IMAGE\",\"stageCode\":\"I00\","
                    + "\"sourceType\":\"USER_GOAL\",\"sourceId\":\"goal\",\"content\":\"测试\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RAG_NAMESPACE_INVALID"));
    }

    @Test
    void chainPrivateRetrievalIncludesAllowedPublicAndProjectKnowledge() throws Exception {
        String chainNamespace = "project:project-1:chain:chain-image-1";
        ingest("global:public", "IMAGE", "I20", "PUBLIC_DOC", "docs/common/04-stage-rubrics.md",
            "公共rubric要求图片提示词必须引用约束");
        ingest("project:project-1", "IMAGE", "I20", "PROJECT_STABLE_KNOWLEDGE", "project-summary",
            "项目稳定知识: 主体是年轻侦探");
        ingest(chainNamespace, "IMAGE", "I20", "CHAIN_CONTEXT", "chain-context",
            "链路私有上下文: 高反差街头夜景");
        ingest("project:project-1:chain:chain-video-1", "VIDEO", "V20", "CHAIN_CONTEXT", "video-context",
            "视频私有上下文不应出现在图片链路");

        mockMvc.perform(post("/api/knowledge:retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + chainNamespace + "\",\"chainType\":\"IMAGE\","
                    + "\"stageCode\":\"I20\",\"query\":\"侦探 约束 夜景\",\"topK\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chunks[*].namespace",
                hasItems("global:public", "project:project-1", chainNamespace)))
            .andExpect(jsonPath("$.data.chunks[*].sourceType",
                hasItems("PUBLIC_DOC", "PROJECT_STABLE_KNOWLEDGE", "CHAIN_CONTEXT")))
            .andExpect(content().string(containsString("公共rubric要求")))
            .andExpect(content().string(containsString("项目稳定知识")))
            .andExpect(content().string(containsString("链路私有上下文")))
            .andExpect(content().string(not(containsString("视频私有上下文"))));
    }

    @Test
    void goalChunkDoesNotSatisfyStageMapOrCurrentStageCoverage() throws Exception {
        String namespace = "project:project-1:chain:chain-image-1";
        ingest(namespace, "IMAGE", "I00", "USER_GOAL", "goal",
            "生成一张9:16都市悬疑短剧封面");

        mockMvc.perform(post("/api/knowledge:retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + namespace + "\",\"chainType\":\"IMAGE\",\"stageCode\":\"I00\","
                    + "\"query\":\"都市悬疑\",\"topK\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coverage.goal").value(true))
            .andExpect(jsonPath("$.data.coverage.stageMap").value(false))
            .andExpect(jsonPath("$.data.coverage.currentStage").value(false))
            .andExpect(jsonPath("$.data.coverage.passed").value(false));
    }

    @Test
    void reportsRequiredCoverageForNonInitialStageEvidence() throws Exception {
        String namespace = "project:project-1:chain:chain-image-1";
        ingest(namespace, "IMAGE", "I30", "USER_GOAL", "goal",
            "userGoal=生成一张9:16都市悬疑短剧封面");
        ingest(namespace, "IMAGE", "I30", "STAGE_MAP", "stage-map",
            "I00->I10->I20->I30->I40->I50->I60");
        ingest(namespace, "IMAGE", "I30", "CHAIN_CONTEXT", "goal",
            "chainType=IMAGE; stageCode=I30; userGoal=生成一张9:16都市悬疑短剧封面");
        ingest(namespace, "IMAGE", "I30", "NEXT_STAGE_CONTEXT", "stage-i20",
            "previous handoff from I20 to I30");
        ingest(namespace, "IMAGE", "I30", "REVIEW_REPORT", "stage-i20",
            "previous review report passed with score 95");

        String response = mockMvc.perform(post("/api/knowledge:retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + namespace + "\",\"chainType\":\"IMAGE\",\"stageCode\":\"I30\","
                    + "\"query\":\"生成一张9:16都市悬疑短剧封面\",\"topK\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coverage.goal").value(true))
            .andExpect(jsonPath("$.data.coverage.currentStage").value(true))
            .andExpect(jsonPath("$.data.coverage.previousHandoff").value(true))
            .andExpect(jsonPath("$.data.coverage.previousReviewReport").value(true))
            .andExpect(jsonPath("$.data.coverage.passed").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String retrievalRecordId = JsonTestSupport.extractString(response, "retrievalRecordId");
        mockMvc.perform(get("/api/retrieval-records/{retrievalRecordId}", retrievalRecordId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.coverage.previousHandoff").value(true))
            .andExpect(jsonPath("$.data.coverage.previousReviewReport").value(true))
            .andExpect(jsonPath("$.data.coverage.passed").value(true));
    }

    @Test
    void rejectsRetrievalWhenEvidenceChunksHaveUnresolvedFieldConflicts() throws Exception {
        String namespace = "project:project-1:chain:chain-image-1";
        ingest(namespace, "IMAGE", "I20", "CHAIN_CONTEXT", "visual-brief",
            "subject=年轻侦探; aspectRatio=9:16; style=电影感");
        ingest(namespace, "IMAGE", "I20", "PROJECT_STABLE_KNOWLEDGE", "project-summary",
            "subject=年轻侦探; aspectRatio=1:1; style=电影感");

        mockMvc.perform(post("/api/knowledge:retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + namespace + "\",\"chainType\":\"IMAGE\",\"stageCode\":\"I20\","
                    + "\"query\":\"年轻侦探 画幅\",\"topK\":5}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("RAG_EVIDENCE_CONFLICT"))
            .andExpect(jsonPath("$.error.message").value(containsString("aspectRatio")));
    }

    private void ingest(String namespace, String chainType, String stageCode, String content) throws Exception {
        ingest(namespace, chainType, stageCode, "USER_GOAL", "source-1", content);
    }

    private void ingest(String namespace, String chainType, String stageCode, String sourceType, String sourceId,
            String content) throws Exception {
        mockMvc.perform(post("/api/knowledge:ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"namespace\":\"" + namespace + "\",\"chainType\":\"" + chainType
                    + "\",\"stageCode\":\"" + stageCode + "\",\"sourceType\":\"" + sourceType
                    + "\",\"sourceId\":\"" + sourceId + "\",\"content\":\"" + content + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentHash").value(containsString("sha256:")));
    }
}
