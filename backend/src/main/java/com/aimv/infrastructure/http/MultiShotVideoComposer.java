package com.aimv.infrastructure.http;

import com.aimv.domain.provider.ProviderHttpRequest;
import com.aimv.shared.error.BusinessException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 多镜头长视频合成器：把故事拆成 N 个镜头，每镜头出一张保持人物一致的图（同 seed + 人物锚点），
 * 逐镜头拼成幻灯片视频，叠加中文旁白，产出 ≥ 目标时长（如 60s）9:16 竖版短剧。
 * 免费模式用 Pollinations 出图（无 key），配音用 DashScope TTS。全部封装在视频适配器内部，
 * core 仍只调一个 video.generate.full_with_voice 能力。人物真实连续运动的增强版走付费 i2v 接力。
 */
final class MultiShotVideoComposer {

    private final RestTemplate restTemplate;
    private final String ttsApiBaseUrl;
    private final VideoVoiceOptions voiceOptions;
    private final PollinationsProviderOptions imageOptions;

    MultiShotVideoComposer(RestTemplate restTemplate, String ttsApiBaseUrl, VideoVoiceOptions voiceOptions,
            PollinationsProviderOptions imageOptions) {
        this.restTemplate = restTemplate;
        this.ttsApiBaseUrl = ttsApiBaseUrl;
        this.voiceOptions = voiceOptions;
        this.imageOptions = imageOptions;
    }

    record ComposedVideo(String publicUrl, int durationSeconds, long byteCount, int shotCount) {
    }

    ComposedVideo compose(ProviderHttpRequest request, String apiKey) {
        String story = story(request);
        int shots = voiceOptions.shotsValue();
        int perShot = voiceOptions.perShotSecondsValue();
        String anchor = StoryboardPlanner.characterAnchor(story);
        List<String> scenes = StoryboardPlanner.shots(story, shots);
        long seed = Math.floorMod(story.hashCode(), 1_000_000);

        Path storageDir = Path.of(voiceOptions.storageDirValue()).toAbsolutePath();
        Path tempDir = null;
        try {
            Files.createDirectories(storageDir);
            tempDir = Files.createTempDirectory("aimv-shots-");
            StringBuilder slides = new StringBuilder();
            for (int index = 0; index < scenes.size(); index++) {
                byte[] image = download(imageUri(anchor + ", " + scenes.get(index), seed));
                Path img = tempDir.resolve("img_" + index + ".jpg");
                Files.write(img, image);
                slides.append("file '").append(img).append("'\n")
                    .append("duration ").append(perShot).append('\n');
            }
            // concat demuxer 要求最后一张再列一次
            slides.append("file '").append(tempDir.resolve("img_" + (scenes.size() - 1) + ".jpg")).append("'\n");
            Path slidesFile = tempDir.resolve("slides.txt");
            Files.writeString(slidesFile, slides.toString());
            Path silent = tempDir.resolve("silent.mp4");
            runSlideshow(slidesFile, silent);

            byte[] audio = synthesizeVoice(apiKey, story);
            Path audioIn = tempDir.resolve("narr.audio");
            Files.write(audioIn, audio);

            String fileName = "drama-" + UUID.randomUUID() + ".mp4";
            Path output = storageDir.resolve(fileName);
            int target = Math.max(scenes.size() * perShot, voiceOptions.targetDurationSecondsValue());
            runMux(silent, audioIn, output, target);
            long size = Files.size(output);
            if (size < 1024) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "MULTISHOT_VIDEO_EMPTY", "多镜头合成视频过小");
            }
            return new ComposedVideo(voiceOptions.publicBaseUrlValue() + "/media/" + fileName, target, size,
                scenes.size());
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "MULTISHOT_IO_FAILED",
                "多镜头合成 IO 失败: " + exception.getMessage());
        } finally {
            deleteQuietly(tempDir);
        }
    }

    private String story(ProviderHttpRequest request) {
        String text = "";
        if (request.input() != null) {
            text = firstText(request.input().get("voiceoverText"), request.input().get("userGoal"),
                request.input().get("prompt"), request.input().get("stageName"));
        }
        return text.isBlank() ? "玄幻短剧" : text;
    }

    private URI imageUri(String prompt, long seed) {
        return UriComponentsBuilder.fromHttpUrl(imageOptions.imageBaseUrlValue())
            .pathSegment("prompt", prompt)
            .queryParam("width", imageOptions.imageWidthValue())
            .queryParam("height", imageOptions.imageHeightValue())
            .queryParam("model", imageOptions.imageModelValue())
            .queryParam("seed", seed)
            .queryParam("nologo", true)
            .build().encode(StandardCharsets.UTF_8).toUri();
    }

    private byte[] synthesizeVoice(String apiKey, String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = Map.of("model", voiceOptions.ttsModelValue(),
                "input", Map.of("text", truncate(text, 400), "voice", voiceOptions.ttsVoiceValue()));
            ResponseEntity<TtsResponse> response = restTemplate.exchange(
                ttsApiBaseUrl + "/services/aigc/multimodal-generation/generation", HttpMethod.POST,
                new HttpEntity<>(body, headers), TtsResponse.class);
            TtsResponse tts = response.getBody();
            if (tts == null || tts.output() == null || tts.output().audio() == null
                    || isBlank(tts.output().audio().url())) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_TTS_RESPONSE_INVALID",
                    "DashScope TTS 响应缺少 audio.url");
            }
            return download(URI.create(tts.output().audio().url()));
        } catch (RestClientException exception) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "DASHSCOPE_TTS_UNAVAILABLE", "DashScope TTS 合成失败");
        }
    }

    private byte[] download(URI uri) {
        // Pollinations 免费层偶发超时/空响应，重试若干次增强健壮性。
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), byte[].class);
                byte[] body = response.getBody();
                if (body != null && body.length >= 512) {
                    return body;
                }
                last = new BusinessException(HttpStatus.BAD_GATEWAY, "MEDIA_DOWNLOAD_EMPTY", "媒体下载为空: " + uri);
            } catch (RestClientException exception) {
                last = new BusinessException(HttpStatus.BAD_GATEWAY, "MEDIA_DOWNLOAD_FAILED",
                    "媒体下载失败: " + uri);
            }
            sleepQuietly(attempt * 1500L);
        }
        throw last;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void runSlideshow(Path slidesFile, Path output) {
        runFfmpeg(List.of(voiceOptions.ffmpegPathValue(), "-y", "-f", "concat", "-safe", "0",
            "-i", slidesFile.toString(),
            "-vf", "scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280,setsar=1,fps=24",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-an", output.toString()),
            "VIDEO_SLIDESHOW_FAILED");
    }

    private void runMux(Path silent, Path audio, Path output, int targetSeconds) {
        runFfmpeg(List.of(voiceOptions.ffmpegPathValue(), "-y",
            "-stream_loop", "-1", "-i", silent.toString(), "-i", audio.toString(),
            "-map", "0:v:0", "-map", "1:a:0",
            "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "aac",
            "-t", String.valueOf(targetSeconds), "-movflags", "+faststart", output.toString()),
            "VIDEO_MUX_FAILED");
    }

    private void runFfmpeg(List<String> command, String failCode) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(300, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, failCode, "ffmpeg 超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, failCode,
                    "ffmpeg 失败，退出码 " + process.exitValue());
            }
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "FFMPEG_UNAVAILABLE",
                "无法启动 ffmpeg: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, failCode, "ffmpeg 被中断");
        }
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }

    private String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
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
