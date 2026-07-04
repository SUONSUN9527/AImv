package com.aimv.application.agent;

import com.aimv.domain.capability.ApiCredential;
import java.util.Map;

public record AgentNodeInput(
        String stageCode,
        String retrievalRecordId,
        ApiCredential credential,
        Map<String, Object> payload
) {
}
