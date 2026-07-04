package com.aimv.domain.chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StageReviewPolicyTest {

    @Test
    void rejectsImageGoalLockWhenGoalAgentMissesRequiredFields() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.IMAGE, "I00", "目标锁定", Map.of(
            "partialOutput", Map.of(
                "subject", "侦探背影",
                "style", "neon suspense",
                "aspectRatio", "9:16",
                "count", 1,
                "goalClarityScore", 95,
                "safetyScore", 100
            )
        ));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("GoalAgent")
            .contains("scene");
    }

    @Test
    void rejectsVideoGoalLockWhenVoiceoverRequirementIsNotNativeHumanVoice() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.VIDEO, "V00", "目标锁定", Map.of(
            "partialOutput", Map.of(
                "theme", "都市悬疑反转",
                "durationSeconds", 10,
                "aspectRatio", "9:16",
                "style", "neon suspense",
                "voiceoverRequirement", "SUBTITLE_ONLY",
                "outputFormat", "complete_short_video",
                "goalClarityScore", 95,
                "safetyScore", 100
            )
        ));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("GoalAgent")
            .contains("voiceoverRequirement");
    }

    @ParameterizedTest
    @CsvSource({
        "IMAGE,I20,图片提示词包",
        "VIDEO,V20,完整视频提示词包"
    })
    void rejectsPromptPackWhenPromptSafetyAgentVetoes(ChainType chainType, String stageCode, String stageName) {
        ReviewReport reviewReport = StageReviewPolicy.review(chainType, stageCode, stageName, Map.of(
            "stageMergedOutput", Map.of(
                "positivePrompt", "neon suspense poster",
                "safetyPassed", false,
                "vetoReason", "CONTENT_SAFETY_REJECTED"
            )
        ));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("PromptSafetyAgent")
            .contains("CONTENT_SAFETY_REJECTED");
    }

    @ParameterizedTest
    @CsvSource({
        "missing variables,neon suspense poster,",
        "unresolved placeholder,portrait of {{subject}},subject=侦探背影"
    })
    void rejectsImagePromptPackWhenPromptVariablesAreNotFullyResolved(String caseName, String positivePrompt,
            String promptVariable) {
        Map<String, Object> stageOutput = new java.util.LinkedHashMap<>();
        stageOutput.put("positivePrompt", positivePrompt);
        stageOutput.put("aspectRatio", "9:16");
        stageOutput.put("safetyPassed", true);
        if (promptVariable != null) {
            stageOutput.put("promptVariables", List.of(promptVariable));
        }

        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.IMAGE, "I20", "图片提示词包", Map.of(
            "stageMergedOutput", stageOutput
        ));

        assertThat(caseName).isNotBlank();
        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("PromptAgent")
            .contains("promptVariables");
    }

    @Test
    void rejectsImagePromptPackWhenPositivePromptIsBlank() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.IMAGE, "I20", "图片提示词包", Map.of(
            "stageMergedOutput", Map.of(
                "positivePrompt", "  ",
                "aspectRatio", "9:16",
                "promptVariables", List.of("subject=侦探背影"),
                "safetyPassed", true
            )
        ));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("PromptAgent")
            .contains("positivePrompt");
    }

    @Test
    void rejectsImageGenerationWhenCandidateEvidenceIsMissing() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.IMAGE, "I40", "图片生成", Map.of(
            "candidateCount", 0,
            "aspectRatio", "9:16",
            "artifactIntegrityScore", 100,
            "providerJobIds", List.of()
        ));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("ImageGenerationAgent")
            .contains("candidateCount")
            .contains("providerJobIds");
    }

    @Test
    void rejectsVideoGenerationWhenCompleteVideoEvidenceIsMissing() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.VIDEO, "V40", "完整短片生成", Map.of(
            "candidateCount", 1,
            "durationSeconds", 8,
            "aspectRatio", "9:16",
            "decodeIntegrityScore", 100,
            "hasHumanVoice", false,
            "providerJobIds", List.of("provider-job-v40")
        ));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("VideoGenerationAgent")
            .contains("durationSeconds")
            .contains("hasHumanVoice");
    }

    @ParameterizedTest
    @CsvSource({
        "continuityConstraints",
        "characterContinuity",
        "motionPrompt",
        "visualStyleConstraint"
    })
    void rejectsVideoPromptPackWhenContinuityConstraintNotEstablished(String constraintField) {
        // 约束由 sibling agent 建立并确定性合并进提示词包；对应约束为空即视为未建立，评审失败。
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.VIDEO, "V20", "完整视频提示词包",
            videoPromptMetadata(Map.of(constraintField, "")));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains(constraintField)
            .contains("must not be blank");
    }

    @Test
    void rejectsVideoPromptPackWhenNativeHumanVoiceRequirementIsMissing() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.VIDEO, "V20", "完整视频提示词包",
            videoPromptMetadata(java.util.Collections.singletonMap("voiceoverRequirement", "")));

        assertThat(reviewReport.passed()).isFalse();
        assertThat(reviewReport.overallScore()).isZero();
        assertThat(reviewReport.summary())
            .contains("voiceoverRequirement")
            .contains("HUMAN_VOICE_REQUIRED");
    }

    @Test
    void acceptsVideoPromptPackWhenAllConstraintsEstablishedAndTargetsMatch() {
        ReviewReport reviewReport = StageReviewPolicy.review(ChainType.VIDEO, "V20", "完整视频提示词包",
            videoPromptMetadata(Map.of()));

        assertThat(reviewReport.passed()).isTrue();
    }

    private Map<String, Object> videoPromptMetadata(Map<String, Object> overrides) {
        Map<String, Object> stageOutput = Map.ofEntries(
            Map.entry("positivePrompt", "complete short drama"),
            Map.entry("durationSeconds", 10),
            Map.entry("aspectRatio", "9:16"),
            Map.entry("voiceoverRequirement", "HUMAN_VOICE_REQUIRED"),
            Map.entry("continuityConstraints", "same subject and scene"),
            Map.entry("characterContinuity", "same detective profile"),
            Map.entry("motionPrompt", "slow push-in with subject movement"),
            Map.entry("visualStyleConstraint", "neon suspense"),
            Map.entry("safetyPassed", true)
        );
        stageOutput = new java.util.LinkedHashMap<>(stageOutput);
        stageOutput.putAll(overrides);
        return Map.of("stageMergedOutput", stageOutput);
    }
}
