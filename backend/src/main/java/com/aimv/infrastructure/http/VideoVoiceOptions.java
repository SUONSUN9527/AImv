package com.aimv.infrastructure.http;

/**
 * 视频链路"完整带配音短片"能力的配音合成配置：t2v 出静音视频后，用 TTS 合成中文旁白，
 * 再用 ffmpeg 叠加音轨、补足到目标时长，产出带人声的完整短片并存到运行时媒体目录对外暴露。
 * 这些机制封装在视频 HTTP 适配器内部，core 仍只调用一个 video.generate.full_with_voice 能力。
 */
record VideoVoiceOptions(
        boolean enabled,
        String ttsModel,
        String ttsVoice,
        String ffmpegPath,
        String storageDir,
        String publicBaseUrl,
        int targetDurationSeconds,
        int shots,
        int perShotSeconds
) {

    private static final String DEFAULT_TTS_MODEL = "qwen-tts";
    private static final String DEFAULT_TTS_VOICE = "Chelsie";
    private static final String DEFAULT_FFMPEG = "ffmpeg";
    private static final String DEFAULT_STORAGE_DIR = "./storage/media";
    private static final String DEFAULT_PUBLIC_BASE_URL = "http://127.0.0.1:8081";
    private static final int DEFAULT_TARGET_DURATION = 10;
    private static final int DEFAULT_PER_SHOT_SECONDS = 5;

    VideoVoiceOptions(boolean enabled, String ttsModel, String ttsVoice, String ffmpegPath, String storageDir,
            String publicBaseUrl, int targetDurationSeconds) {
        this(enabled, ttsModel, ttsVoice, ffmpegPath, storageDir, publicBaseUrl, targetDurationSeconds, 0,
            DEFAULT_PER_SHOT_SECONDS);
    }

    static VideoVoiceOptions disabled() {
        return new VideoVoiceOptions(false, DEFAULT_TTS_MODEL, DEFAULT_TTS_VOICE, DEFAULT_FFMPEG,
            DEFAULT_STORAGE_DIR, DEFAULT_PUBLIC_BASE_URL, DEFAULT_TARGET_DURATION, 0, DEFAULT_PER_SHOT_SECONDS);
    }

    /** 多镜头模式：>0 表示用 N 个镜头拼长视频（如 12 镜头×5s≈60s）；0 表示单镜头 t2v。 */
    int shotsValue() {
        return Math.max(shots, 0);
    }

    int perShotSecondsValue() {
        return perShotSeconds > 0 ? perShotSeconds : DEFAULT_PER_SHOT_SECONDS;
    }

    boolean multiShotEnabled() {
        return shotsValue() > 0;
    }

    String ttsModelValue() {
        return blankToDefault(ttsModel, DEFAULT_TTS_MODEL);
    }

    String ttsVoiceValue() {
        return blankToDefault(ttsVoice, DEFAULT_TTS_VOICE);
    }

    String ffmpegPathValue() {
        return blankToDefault(ffmpegPath, DEFAULT_FFMPEG);
    }

    String storageDirValue() {
        return blankToDefault(storageDir, DEFAULT_STORAGE_DIR);
    }

    String publicBaseUrlValue() {
        return trimRightSlash(blankToDefault(publicBaseUrl, DEFAULT_PUBLIC_BASE_URL));
    }

    int targetDurationSecondsValue() {
        return targetDurationSeconds > 0 ? targetDurationSeconds : DEFAULT_TARGET_DURATION;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String trimRightSlash(String value) {
        return value.endsWith("/") ? trimRightSlash(value.substring(0, value.length() - 1)) : value;
    }
}
