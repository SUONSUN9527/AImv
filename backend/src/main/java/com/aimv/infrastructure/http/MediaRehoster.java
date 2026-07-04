package com.aimv.infrastructure.http;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

/**
 * 把 provider 返回的「会过期的临时媒体链接」（如 DashScope 图片/视频的 OSS 预签名 URL）
 * 下载后转存到本地 /media 目录，返回永久可访问的 /media URL。
 * 这样历史生成的图/视频不会因为 OSS 链接过期而在前端变成「预览不可用」。
 * 用 java.net 直连下载二进制（比 RestTemplate 的消息转换更稳）；任何失败都退回原始 URL——
 * 转存只是增强，绝不阻断生成本身。
 */
final class MediaRehoster {

    private MediaRehoster() {
    }

    static List<String> rehostAll(List<String> urls, String storageDir, String publicBaseUrl) {
        if (urls == null || urls.isEmpty()) {
            return urls;
        }
        return urls.stream().map(url -> rehost(url, storageDir, publicBaseUrl)).toList();
    }

    static String rehost(String url, String storageDir, String publicBaseUrl) {
        if (url == null || url.isBlank() || !url.startsWith("http")) {
            return url;
        }
        String base = trimSlash(publicBaseUrl);
        if (!base.isEmpty() && url.startsWith(base + "/media/")) {
            return url; // 已经是本地永久链接
        }
        try {
            byte[] bytes = download(url);
            if (bytes.length == 0) {
                return url;
            }
            String name = "gen-" + sha1(bytes).substring(0, 24) + extension(url);
            Path dir = Path.of(storageDir).toAbsolutePath();
            Files.createDirectories(dir);
            Path file = dir.resolve(name);
            if (!Files.exists(file)) {
                Files.write(file, bytes);
            }
            return base + "/media/" + name;
        } catch (Exception exception) {
            return url; // 转存失败：仍用原(可能过期)链接，不影响本次生成
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "aimv-rehoster/1.0");
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    private static String extension(String url) {
        String clean = url.split("[?#]", 2)[0].toLowerCase();
        for (String ext : List.of(".png", ".jpeg", ".jpg", ".webp", ".mp4", ".gif")) {
            if (clean.endsWith(ext)) {
                return ext;
            }
        }
        return ".png";
    }

    private static String sha1(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-1").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
