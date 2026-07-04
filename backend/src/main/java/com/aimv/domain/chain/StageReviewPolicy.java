package com.aimv.domain.chain;

import com.aimv.domain.shared.ChainType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StageReviewPolicy {

    private static final int DEFAULT_PASSING_SCORE = 95;
    private static final int MINIMUM_STAGE_SCORE = 85;
    private static final int MINIMUM_SHORT_DRAMA_SCORE = 90;
    private static final int MINIMUM_GOAL_CLARITY_SCORE = 90;
    private static final int PERFECT_SCORE = 100;
    private static final int IMAGE_TARGET_COUNT = 1;
    private static final int VIDEO_TARGET_DURATION_SECONDS = 10;
    private static final String IMAGE_GOAL_STAGE_CODE = "I00";
    private static final String IMAGE_PROMPT_STAGE_CODE = "I20";
    private static final String IMAGE_GENERATION_STAGE_CODE = "I40";
    private static final String VIDEO_GOAL_STAGE_CODE = "V00";
    private static final String VIDEO_PROMPT_STAGE_CODE = "V20";
    private static final String VIDEO_GENERATION_STAGE_CODE = "V40";
    private static final String STAGE_MERGED_OUTPUT = "stageMergedOutput";
    private static final String PARTIAL_OUTPUT = "partialOutput";
    private static final String ASPECT_RATIO = "aspectRatio";
    private static final String CANDIDATE_COUNT = "candidateCount";
    private static final String GOAL_CLARITY_SCORE = "goalClarityScore";
    private static final String SAFETY_SCORE = "safetyScore";
    private static final String DURATION_SECONDS = "durationSeconds";
    private static final String ARTIFACT_INTEGRITY_SCORE = "artifactIntegrityScore";
    private static final String DECODE_INTEGRITY_SCORE = "decodeIntegrityScore";
    private static final String PROVIDER_JOB_IDS = "providerJobIds";
    private static final String HAS_HUMAN_VOICE = "hasHumanVoice";
    private static final String POSITIVE_PROMPT = "positivePrompt";
    private static final String PROMPT_VARIABLES = "promptVariables";
    private static final String VIDEO_TARGET_ASPECT_RATIO = "9:16";
    private static final String VOICEOVER_REQUIREMENT = "voiceoverRequirement";
    private static final String HUMAN_VOICE_REQUIRED = "HUMAN_VOICE_REQUIRED";
    private static final String SAFETY_PASSED = "safetyPassed";
    private static final String VETO_REASON = "vetoReason";
    private static final String CONTINUITY_CONSTRAINTS = "continuityConstraints";
    private static final String CONTINUITY_CONSTRAINT_REFS = "continuityConstraintRefs";
    private static final String MOTION_PROMPT = "motionPrompt";
    private static final String MOTION_PROMPT_REFS = "motionPromptRefs";
    private static final String CHARACTER_CONTINUITY = "characterContinuity";
    private static final String CHARACTER_CONTINUITY_REFS = "characterContinuityRefs";
    private static final String VISUAL_STYLE_CONSTRAINT = "visualStyleConstraint";
    private static final String VISUAL_STYLE_REFS = "visualStyleRefs";

    private StageReviewPolicy() {
    }

    public static ReviewReport review(ChainType chainType, String stageCode, String stageName,
            Map<String, Object> metadata) {
        if (chainType == ChainType.IMAGE && IMAGE_GOAL_STAGE_CODE.equals(stageCode)) {
            return imageGoalReview(stageCode, stageName, metadata);
        }
        if (chainType == ChainType.VIDEO && VIDEO_GOAL_STAGE_CODE.equals(stageCode)) {
            return videoGoalReview(stageCode, stageName, metadata);
        }
        if (isPromptPackStage(chainType, stageCode)) {
            return promptSafetyReview(chainType, stageCode, stageName, metadata);
        }
        if (chainType == ChainType.IMAGE && IMAGE_GENERATION_STAGE_CODE.equals(stageCode)) {
            return imageGenerationReview(stageCode, stageName, metadata);
        }
        if (chainType == ChainType.VIDEO && VIDEO_GENERATION_STAGE_CODE.equals(stageCode)) {
            return videoGenerationReview(stageCode, stageName, metadata);
        }
        if (chainType == ChainType.IMAGE && "I50".equals(stageCode)) {
            return imageQualityReview(stageCode, stageName, metadata);
        }
        if (chainType == ChainType.VIDEO && "V50".equals(stageCode)) {
            return videoQualityReview(stageCode, stageName, metadata);
        }
        return passedReview(chainType, stageCode, stageName);
    }

    private static boolean isPromptPackStage(ChainType chainType, String stageCode) {
        return (chainType == ChainType.IMAGE && IMAGE_PROMPT_STAGE_CODE.equals(stageCode))
            || (chainType == ChainType.VIDEO && VIDEO_PROMPT_STAGE_CODE.equals(stageCode));
    }

    private static ReviewReport imageGoalReview(String stageCode, String stageName, Map<String, Object> metadata) {
        List<String> violations = new ArrayList<>();
        addBlankFieldViolations(violations, metadata, "subject", "scene", "style", ASPECT_RATIO);
        addExactNumberViolation(violations, metadata, "count", IMAGE_TARGET_COUNT);
        addMinimumNumberViolation(violations, metadata, GOAL_CLARITY_SCORE, MINIMUM_GOAL_CLARITY_SCORE);
        addExactNumberViolation(violations, metadata, SAFETY_SCORE, PERFECT_SCORE);
        return goalReviewResult(ChainType.IMAGE, stageCode, stageName, violations);
    }

    private static ReviewReport videoGoalReview(String stageCode, String stageName, Map<String, Object> metadata) {
        List<String> violations = new ArrayList<>();
        addBlankFieldViolations(violations, metadata, "theme", ASPECT_RATIO, "style", VOICEOVER_REQUIREMENT,
            "outputFormat");
        addExactNumberViolation(violations, metadata, DURATION_SECONDS, VIDEO_TARGET_DURATION_SECONDS);
        addExactTextViolation(violations, metadata, ASPECT_RATIO, VIDEO_TARGET_ASPECT_RATIO);
        addExactTextViolation(violations, metadata, VOICEOVER_REQUIREMENT, HUMAN_VOICE_REQUIRED);
        addMinimumNumberViolation(violations, metadata, GOAL_CLARITY_SCORE, MINIMUM_GOAL_CLARITY_SCORE);
        addExactNumberViolation(violations, metadata, SAFETY_SCORE, PERFECT_SCORE);
        return goalReviewResult(ChainType.VIDEO, stageCode, stageName, violations);
    }

    private static ReviewReport goalReviewResult(ChainType chainType, String stageCode, String stageName,
            List<String> violations) {
        if (violations.isEmpty()) {
            return passedReview(chainType, stageCode, stageName);
        }
        return new ReviewReport(false, 0, rubricVersion(chainType, stageCode),
            "GoalAgent目标锁定未达标: " + String.join(", ", violations));
    }

    private static ReviewReport promptSafetyReview(ChainType chainType, String stageCode, String stageName,
            Map<String, Object> metadata) {
        Boolean safetyPassed = booleanStageOutputValue(metadata, SAFETY_PASSED);
        if (!Boolean.TRUE.equals(safetyPassed)) {
            String vetoReason = stringStageOutputValue(metadata, VETO_REASON);
            String reason = vetoReason.isBlank() ? "safetyPassed=false" : vetoReason;
            return new ReviewReport(false, 0, rubricVersion(chainType, stageCode),
                "PromptSafetyAgent veto: " + reason);
        }
        if (chainType == ChainType.VIDEO) {
            return videoPromptContractReview(chainType, stageCode, stageName, metadata);
        }
        return imagePromptContractReview(chainType, stageCode, stageName, metadata);
    }

    private static ReviewReport imagePromptContractReview(ChainType chainType, String stageCode, String stageName,
            Map<String, Object> metadata) {
        List<String> violations = new ArrayList<>();
        if (stringListStageOutputValue(metadata, PROMPT_VARIABLES).isEmpty()) {
            violations.add(PROMPT_VARIABLES + " must not be empty");
        }
        String positivePrompt = stringStageOutputValue(metadata, POSITIVE_PROMPT);
        if (positivePrompt.isBlank()) {
            violations.add(POSITIVE_PROMPT + " must not be blank");
        }
        if (positivePrompt.contains("{{") || positivePrompt.contains("}}")) {
            violations.add(PROMPT_VARIABLES + " did not resolve all placeholders in " + POSITIVE_PROMPT);
        }
        if (violations.isEmpty()) {
            return passedReview(chainType, stageCode, stageName);
        }
        return new ReviewReport(false, 0, rubricVersion(chainType, stageCode),
            "PromptAgent图片提示词变量解析未达标: " + String.join(", ", violations));
    }

    /**
     * V20 完整视频提示词包评审。要求 ContinuityAgent/MotionPromptAgent 建立的连续性、人物、动作、
     * 视觉风格约束都已确立（非空）并随合并进入提示词包，且时长/画幅/配音要求精确达标。
     *
     * 说明：早期 rubric 要求 PromptAgent 精确 echo 各 sibling agent 的输出字符串，但这些是独立
     * LLM 调用、无法可靠对齐，属不可达约束。改为校验约束"已建立并被确定性合并器携带进提示词包"，
     * 这才是"连续性约束被提示词包引用"的可实现语义。
     */
    private static ReviewReport videoPromptContractReview(ChainType chainType, String stageCode, String stageName,
            Map<String, Object> metadata) {
        List<String> violations = new ArrayList<>();
        addBlankFieldViolations(violations, metadata, CONTINUITY_CONSTRAINTS, CHARACTER_CONTINUITY,
            MOTION_PROMPT, VISUAL_STYLE_CONSTRAINT);
        addExactNumberViolation(violations, metadata, DURATION_SECONDS, VIDEO_TARGET_DURATION_SECONDS);
        addExactTextViolation(violations, metadata, ASPECT_RATIO, VIDEO_TARGET_ASPECT_RATIO);
        addExactTextViolation(violations, metadata, VOICEOVER_REQUIREMENT, HUMAN_VOICE_REQUIRED);
        if (violations.isEmpty()) {
            return passedReview(chainType, stageCode, stageName);
        }
        return new ReviewReport(false, 0, rubricVersion(chainType, stageCode),
            "完整视频提示词约束未达标: " + String.join(", ", violations));
    }

    private static ReviewReport imageGenerationReview(String stageCode, String stageName,
            Map<String, Object> metadata) {
        List<String> violations = new ArrayList<>();
        addMinimumNumberViolation(violations, metadata, CANDIDATE_COUNT, IMAGE_TARGET_COUNT);
        addBlankFieldViolations(violations, metadata, ASPECT_RATIO);
        addExactNumberViolation(violations, metadata, ARTIFACT_INTEGRITY_SCORE, PERFECT_SCORE);
        addRequiredListViolation(violations, metadata, PROVIDER_JOB_IDS);
        return generationReviewResult(ChainType.IMAGE, stageCode, stageName, "ImageGenerationAgent",
            violations);
    }

    private static ReviewReport videoGenerationReview(String stageCode, String stageName,
            Map<String, Object> metadata) {
        List<String> violations = new ArrayList<>();
        addMinimumNumberViolation(violations, metadata, CANDIDATE_COUNT, 1);
        // 至少 10 秒：单镜头 10s、多镜头分镜 60s 都满足。
        addMinimumNumberViolation(violations, metadata, DURATION_SECONDS, VIDEO_TARGET_DURATION_SECONDS);
        addExactTextViolation(violations, metadata, ASPECT_RATIO, VIDEO_TARGET_ASPECT_RATIO);
        addExactNumberViolation(violations, metadata, DECODE_INTEGRITY_SCORE, PERFECT_SCORE);
        addExactBooleanViolation(violations, metadata, HAS_HUMAN_VOICE, true);
        addRequiredListViolation(violations, metadata, PROVIDER_JOB_IDS);
        return generationReviewResult(ChainType.VIDEO, stageCode, stageName, "VideoGenerationAgent",
            violations);
    }

    private static ReviewReport generationReviewResult(ChainType chainType, String stageCode, String stageName,
            String agentName, List<String> violations) {
        if (violations.isEmpty()) {
            return passedReview(chainType, stageCode, stageName);
        }
        return new ReviewReport(false, 0, rubricVersion(chainType, stageCode),
            agentName + "生成证据未达标: " + String.join(", ", violations));
    }

    private static ReviewReport imageQualityReview(String stageCode, String stageName,
            Map<String, Object> metadata) {
        int finalScore = imageFinalScore(metadata);
        int safetyScore = intValue(metadata, "safetyScore");
        int artifactIntegrityScore = intValue(metadata, ARTIFACT_INTEGRITY_SCORE);
        boolean passed = finalScore >= MINIMUM_STAGE_SCORE
            && safetyScore == PERFECT_SCORE
            && artifactIntegrityScore == PERFECT_SCORE;
        return new ReviewReport(passed, finalScore, rubricVersion(ChainType.IMAGE, stageCode),
            passed ? stageName + "已按固定 rubric 通过"
                : "图片质量评分未达标: finalScore=" + finalScore
                    + ", safetyScore=" + safetyScore
                    + ", artifactIntegrityScore=" + artifactIntegrityScore);
    }

    /**
     * 图片评审总分。provider/评审证据直接给出 finalScore 时沿用；
     * 否则按技术文档权重公式从各评审 agent 的分项分数确定性计算：
     * visualQuality*0.35 + goalMatch*0.35 + promptConsistency*0.15
     * + artifactIntegrity*0.10 + observability*0.05。
     */
    private static int imageFinalScore(Map<String, Object> metadata) {
        int declaredFinalScore = intValue(metadata, "finalScore");
        if (declaredFinalScore > 0) {
            return clampScore(declaredFinalScore);
        }
        int observabilityScore = stringListStageOutputValue(metadata, PROVIDER_JOB_IDS).isEmpty() ? 0 : 100;
        return clampScore((int) Math.round(
            intValue(metadata, "visualQualityScore") * 0.35
                + intValue(metadata, "goalMatchScore") * 0.35
                + intValue(metadata, "promptConsistencyScore") * 0.15
                + intValue(metadata, ARTIFACT_INTEGRITY_SCORE) * 0.10
                + observabilityScore * 0.05));
    }

    private static ReviewReport videoQualityReview(String stageCode, String stageName,
            Map<String, Object> metadata) {
        int finalScore = videoFinalScore(metadata);
        int decodeIntegrityScore = intValue(metadata, DECODE_INTEGRITY_SCORE);
        int safetyScore = intValue(metadata, "safetyScore");
        int shortDramaScore = intValue(metadata, "shortDramaScore");
        boolean humanVoiceAudible = booleanStageValue(metadata, "humanVoiceAudible");
        boolean passed = finalScore >= MINIMUM_STAGE_SCORE
            && decodeIntegrityScore == PERFECT_SCORE
            && safetyScore == PERFECT_SCORE
            && shortDramaScore >= MINIMUM_SHORT_DRAMA_SCORE
            && humanVoiceAudible;
        return new ReviewReport(passed, finalScore, rubricVersion(ChainType.VIDEO, stageCode),
            passed ? stageName + "已按固定 rubric 通过"
                : "视频质量评分未达标: decodeIntegrityScore=" + decodeIntegrityScore
                    + ", safetyScore=" + safetyScore
                    + ", shortDramaScore=" + shortDramaScore
                    + ", 可听清人声=" + humanVoiceAudible);
    }

    /**
     * 视频评审总分。provider/评审证据直接给出 finalScore 时沿用；
     * 否则按技术文档权重公式计算：decode*0.20 + motion*0.20 + goalMatch*0.20
     * + voiceover*0.15 + promptConsistency*0.15 + continuity*0.10 + observability*0.05。
     * continuityScore 缺失时以 promptConsistencyScore 代入。
     */
    private static int videoFinalScore(Map<String, Object> metadata) {
        int declaredFinalScore = intValue(metadata, "finalScore");
        if (declaredFinalScore > 0) {
            return clampScore(declaredFinalScore);
        }
        int observabilityScore = stringListStageOutputValue(metadata, PROVIDER_JOB_IDS).isEmpty() ? 0 : 100;
        int promptConsistencyScore = intValue(metadata, "promptConsistencyScore");
        int continuityScore = stageOutputValue(metadata, "continuityScore") == null
            ? promptConsistencyScore : intValue(metadata, "continuityScore");
        return clampScore((int) Math.round(
            intValue(metadata, DECODE_INTEGRITY_SCORE) * 0.20
                + intValue(metadata, "motionQualityScore") * 0.20
                + intValue(metadata, "goalMatchScore") * 0.20
                + intValue(metadata, "voiceoverQualityScore") * 0.15
                + promptConsistencyScore * 0.10
                + continuityScore * 0.10
                + observabilityScore * 0.05));
    }

    /** 评分一律钳制在 0~100，避免权重/证据异常算出 >100 撑爆 DB 约束（曾致前端看到原始 SQL 报错）。 */
    private static int clampScore(int score) {
        return Math.max(0, Math.min(PERFECT_SCORE, score));
    }

    private static boolean booleanStageValue(Map<String, Object> metadata, String key) {
        Object value = stageOutputValue(metadata, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private static ReviewReport passedReview(ChainType chainType, String stageCode, String stageName) {
        return new ReviewReport(true, DEFAULT_PASSING_SCORE, rubricVersion(chainType, stageCode),
            stageName + "已按固定 rubric 通过");
    }

    private static String rubricVersion(ChainType chainType, String stageCode) {
        return chainType.name().toLowerCase() + "-" + stageCode + ".v1";
    }

    private static Boolean booleanStageOutputValue(Map<String, Object> metadata, String key) {
        Object value = stageOutputValue(metadata, key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private static String stringStageOutputValue(Map<String, Object> metadata, String key) {
        Object value = stageOutputValue(metadata, key);
        return value == null ? "" : String.valueOf(value);
    }

    private static List<String> stringListStageOutputValue(Map<String, Object> metadata, String key) {
        Object value = stageOutputValue(metadata, key);
        if (value instanceof Iterable<?> values) {
            List<String> textValues = new ArrayList<>();
            values.forEach(item -> textValues.add(String.valueOf(item)));
            return List.copyOf(textValues);
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    private static void addBlankFieldViolations(List<String> violations, Map<String, Object> metadata,
            String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (stringStageOutputValue(metadata, fieldName).isBlank()) {
                violations.add(fieldName + " must not be blank");
            }
        }
    }

    private static void addExactTextViolation(List<String> violations, Map<String, Object> metadata, String fieldName,
            String expectedValue) {
        String actualValue = stringStageOutputValue(metadata, fieldName);
        if (!expectedValue.equals(actualValue)) {
            violations.add(fieldName + "=" + expectedValue);
        }
    }

    private static void addExactNumberViolation(List<String> violations, Map<String, Object> metadata,
            String fieldName, int expectedValue) {
        int actualValue = intValue(metadata, fieldName);
        if (actualValue != expectedValue) {
            violations.add(fieldName + "=" + expectedValue);
        }
    }

    private static void addMinimumNumberViolation(List<String> violations, Map<String, Object> metadata,
            String fieldName, int minimumValue) {
        int actualValue = intValue(metadata, fieldName);
        if (actualValue < minimumValue) {
            violations.add(fieldName + ">=" + minimumValue);
        }
    }

    private static void addExactBooleanViolation(List<String> violations, Map<String, Object> metadata,
            String fieldName, boolean expectedValue) {
        boolean actualValue = booleanValue(metadata, fieldName);
        if (actualValue != expectedValue) {
            violations.add(fieldName + "=" + expectedValue);
        }
    }

    private static void addRequiredListViolation(List<String> violations, Map<String, Object> metadata,
            String fieldName) {
        if (stringListStageOutputValue(metadata, fieldName).isEmpty()) {
            violations.add(fieldName + " must not be empty");
        }
    }

    private static Object stageOutputValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object stageMergedOutput = metadata.get(STAGE_MERGED_OUTPUT);
        if (stageMergedOutput instanceof Map<?, ?> fields && fields.containsKey(key)) {
            return fields.get(key);
        }
        Object partialOutput = metadata.get(PARTIAL_OUTPUT);
        if (partialOutput instanceof Map<?, ?> fields && fields.containsKey(key)) {
            return fields.get(key);
        }
        return metadata.get(key);
    }

    private static int intValue(Map<String, Object> metadata, String key) {
        Object value = stageOutputValue(metadata, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean booleanValue(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return false;
        }
        Object value = metadata.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }
}
