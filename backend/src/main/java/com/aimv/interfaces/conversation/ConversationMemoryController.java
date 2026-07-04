package com.aimv.interfaces.conversation;

import com.aimv.application.conversation.ConversationMemoryService;
import com.aimv.shared.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 连续对话记忆压缩：前端把各轮原始请求发来，后端返回压缩后的「对话记忆」注入下一轮生成。 */
@RestController
@RequestMapping("/api")
public class ConversationMemoryController {

    private final ConversationMemoryService conversationMemoryService;

    public ConversationMemoryController(ConversationMemoryService conversationMemoryService) {
        this.conversationMemoryService = conversationMemoryService;
    }

    @PostMapping("/conversation-memory")
    public ApiResponse<Map<String, String>> build(@RequestBody ConversationMemoryRequest request) {
        String memory = conversationMemoryService.build(request == null ? List.of() : request.requests());
        return ApiResponse.ok(Map.of("memory", memory));
    }

    public record ConversationMemoryRequest(List<String> requests) {
    }
}
