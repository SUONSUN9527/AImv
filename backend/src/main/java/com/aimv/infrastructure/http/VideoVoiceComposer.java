package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.shared.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 把 t2v 产出的静音视频合成为带中文人声旁白的完整短片：调用 DashScope TTS 合成旁白音频，
 * 下载视频与音频，用 ffmpeg 叠加音轨并补足/裁剪到目标时长，写入运行时媒体目录并返回可播放 URL。
 * 全部封装在视频适配器内部，属基础设施机制，不进入 domain/application。
 */
final class VideoVoiceComposer {

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final VideoVoiceOptions options;

    VideoVoiceComposer(RestTemplate restTemplate, String apiBaseUrl, VideoVoiceOptions options) {
        this.restTemplate = restTemplate;
        this.apiBaseUrl = apiBaseUrl;
        this.options = options;
    }

    record ComposedVideo(String publicUrl, int durationSeconds, long byteCount) {
    }

    ComposedVideo compose(ProviderHttpRequest request, String apiKey, String silentVideoUrl) {
        String narration = narration(request);
        byte[] audio = synthesizeVoice(apiKey, narration);
        byte[] video = download(silentVideoUrl);
        return mux(video, audio);
    }

    private String narration(ProviderHttpRequest request) {
        String text = "";
        if (request.input() != null) {
            text = firstText(request.input().get("voiceoverText"), request.input().get("narration"),
                request.input().get("userGoal"), request.input().get("prompt"));
        }
        if (text.isBlank()) {
            text = "玄幻短剧旁白";
        }
        // 10 秒中文旁白约 30-40 字，超出截断到首句/前 40 字，避免配音过长。
        String normalized = text.replaceAll("\\s+", " ").strip();
        int cut = normalized.length();
        for (int index = 0; index < normalized.length() && index < 44; index++) {
            char c = normalized.charAt(index);
            if ((c == '。' || c == '！' || c == '？' || c == '.') && index >= 12) {
                cut = index + 1;
                break;
            }
        }
        cut = Math.min(cut, 44);
        return normalized.substring(0, Math.min(cut, normalized.length()));
    }

    private byte[] synthesizeVoice(String apiKey, String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = Map.of(
                "model", options.ttsModelValue(),
                "input", Map.of("text", text, "voice", options.ttsVoiceValue()));
            ResponseEntity<TtsResponse> response = restTemplate.exchange(ttsEndpoint(), HttpMethod.POST,
                new HttpEntity<>(body, headers), TtsResponse.class);
            TtsResponse tts = response.getBody();
            if (tts == null || tts.output() == null || tts.output().audio() == null
                    || isBlank(tts.output().audio().url())) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_TTS_RESPONSE_INVALID",
                    "DashScope TTS 响应缺少 audio.url");
            }
            return download(tts.output().audio().url());
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_TTS_UNAVAILABLE",
                "DashScope TTS 合成失败");
        }
    }

    private byte[] download(String url) {
        try {
            // 用 URI 而非 String：OSS 预签名 URL 的 Signature 含 %3D 等已编码字符，
            // 传 String 会被 RestTemplate 当模板二次编码导致签名失效（403）。
            ResponseEntity<byte[]> response = restTemplate.exchange(java.net.URI.create(url), HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "MEDIA_DOWNLOAD_EMPTY",
                    "媒体下载为空: " + url);
            }
            return body;
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "MEDIA_DOWNLOAD_FAILED",
                "媒体下载失败: " + url);
        }
    }

    private ComposedVideo mux(byte[] video, byte[] audio) {
        Path storageDir = Path.of(options.storageDirValue()).toAbsolutePath();
        Path tempDir = null;
        try {
            Files.createDirectories(storageDir);
            tempDir = Files.createTempDirectory("aimv-mux-");
            Path videoIn = tempDir.resolve("in.mp4");
            Path audioIn = tempDir.resolve("in.audio");
            Files.write(videoIn, video);
            Files.write(audioIn, audio);
            String fileName = "video-" + UUID.randomUUID() + ".mp4";
            Path output = storageDir.resolve(fileName);
            int target = options.targetDurationSecondsValue();
            runFfmpeg(videoIn, audioIn, output, target);
            long size = Files.size(output);
            if (size < 1024) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "VIDEO_MUX_EMPTY", "合成视频过小");
            }
            return new ComposedVideo(options.publicBaseUrlValue() + "/media/" + fileName, target, size);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_MUX_IO_FAILED",
                "视频合成 IO 失败: " + exception.getMessage());
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private void runFfmpeg(Path videoIn, Path audioIn, Path output, int targetSeconds) {
        try {
            Process process = new ProcessBuilder(options.ffmpegPathValue(), "-y",
                "-stream_loop", "-1", "-i", videoIn.toString(),
                "-i", audioIn.toString(),
                "-map", "0:v:0", "-map", "1:a:0",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac",
                "-t", String.valueOf(targetSeconds), "-movflags", "+faststart",
                output.toString())
                .redirectErrorStream(true)
                .start();
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_MUX_TIMEOUT",
                    "ffmpeg 合成超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_MUX_FAILED",
                    "ffmpeg 合成失败，退出码 " + process.exitValue());
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "FFMPEG_UNAVAILABLE",
                "无法启动 ffmpeg（请确认已安装并在 PATH）: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_MUX_INTERRUPTED",
                "ffmpeg 合成被中断");
        }
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort 清理
                }
            });
        } catch (IOException ignored) {
            // best-effort 清理
        }
    }

    private String ttsEndpoint() {
        return apiBaseUrl + "/services/aigc/multimodal-generation/generation";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TtsResponse(@JsonProperty("request_id") String requestId, TtsOutput output) {
    }

    private record TtsOutput(TtsAudio audio) {
    }

    private record TtsAudio(String url) {
    }
}
