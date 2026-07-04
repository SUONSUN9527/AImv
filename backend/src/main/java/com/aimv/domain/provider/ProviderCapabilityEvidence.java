package com.aimv.domain.provider;

import java.util.Map;

public final class ProviderCapabilityEvidence {

    public static final String VIDEO_FULL_WITH_VOICE_CAPABILITY = "video.generate.full_with_voice.free";

    private ProviderCapabilityEvidence() {
    }

    public static boolean satisfiesCapability(String capabilityType, Map<String, Object> metadata) {
        if (!VIDEO_FULL_WITH_VOICE_CAPABILITY.equals(capabilityType)) {
            return true;
        }
        // 时长要求：至少 10 秒完整短片。单镜头 t2v 交付 10s，多镜头分镜交付更长（如 60s）都满足。
        return isTrue(metadata, "completeShortVideoSupported")
            && isTrue(metadata, "nativeHumanVoiceSupported")
            && numberAtLeast(metadata, "durationSeconds", 10)
            && "9:16".equals(stringValue(metadata, "aspectRatio"));
    }

    public static String failureSummary(String capabilityType) {
        if (VIDEO_FULL_WITH_VOICE_CAPABILITY.equals(capabilityType)) {
            return "provider 未证明支持 10 秒、9:16、原生人声配音完整短片，"
                + "进入 WAITING_CAPABILITY";
        }
        return "provider 能力证据不足，进入 WAITING_CAPABILITY";
    }

    private static boolean isTrue(Map<String, Object> metadata, String key) {
        return metadata != null && Boolean.TRUE.equals(metadata.get(key));
    }

    private static boolean numberAtLeast(Map<String, Object> metadata, String key, int minimum) {
        if (metadata == null) {
            return false;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue() >= minimum;
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text) >= minimum;
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return false;
    }

    private static String stringValue(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) {
            return "";
        }
        return String.valueOf(metadata.get(key));
    }
}
