package com.aimv.interfaces.knowledge;

import com.aimv.application.knowledge.KnowledgeApplicationService;
import com.aimv.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class KnowledgeController {

    private final KnowledgeApplicationService knowledgeApplicationService;

    public KnowledgeController(KnowledgeApplicationService knowledgeApplicationService) {
        this.knowledgeApplicationService = knowledgeApplicationService;
    }

    @PostMapping("/knowledge:ingest")
    public ApiResponse<KnowledgeChunkDto> ingest(@Valid @RequestBody IngestKnowledgeRequest request) {
        return ApiResponse.ok(KnowledgeChunkDto.from(knowledgeApplicationService.ingest(request.namespace(),
            request.chainType(), request.stageCode(), request.sourceType(), request.sourceId(), request.content())));
    }

    @PostMapping("/knowledge:retrieve")
    public ApiResponse<RetrieveKnowledgeResponse> retrieve(@Valid @RequestBody RetrieveKnowledgeRequest request) {
        return ApiResponse.ok(RetrieveKnowledgeResponse.from(knowledgeApplicationService.retrieve(request.namespace(),
            request.chainType(), request.stageCode(), request.query(), request.topK())));
    }

    @GetMapping("/retrieval-records/{retrievalRecordId}")
    public ApiResponse<RetrievalRecordDto> record(@PathVariable String retrievalRecordId) {
        return ApiResponse.ok(RetrievalRecordDto.from(knowledgeApplicationService.record(retrievalRecordId)));
    }

    @PostMapping("/knowledge:reindex")
    public ApiResponse<ReindexKnowledgeResponse> reindex(@Valid @RequestBody ReindexKnowledgeRequest request) {
        return ApiResponse.ok(ReindexKnowledgeResponse.from(knowledgeApplicationService.reindex(request.namespace(),
            request.chainType())));
    }
}
