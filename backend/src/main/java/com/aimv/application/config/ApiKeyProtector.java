package com.aimv.application.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * API key 加解密。
 *
 * 密钥来源优先级：
 * 1. 环境变量 AIMV_CREDENTIAL_AES_KEY（Base64 编码的 32 字节密钥），适合生产部署。
 * 2. 本地密钥文件（默认 ~/.aimv/credential.key，可用 AIMV_CREDENTIAL_KEY_FILE 覆盖）。
 *    文件不存在时自动生成并持久化，保证后端重启后已保存的加密 key 仍可解密。
 */
final class ApiKeyProtector {

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final String KEY_ENV_NAME = "AIMV_CREDENTIAL_AES_KEY";
    private static final String KEY_FILE_ENV_NAME = "AIMV_CREDENTIAL_KEY_FILE";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static volatile SecretKey localKey;

    private ApiKeyProtector() {
    }

    static String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("API key encryption failed", exception);
        }
    }

    static String decrypt(String encryptedText) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("API key decryption failed", exception);
        }
    }

    private static SecretKey key() {
        SecretKey current = localKey;
        if (current != null) {
            return current;
        }
        synchronized (ApiKeyProtector.class) {
            if (localKey == null) {
                localKey = loadOrCreateKey();
            }
            return localKey;
        }
    }

    private static SecretKey loadOrCreateKey() {
        String envKey = System.getenv(KEY_ENV_NAME);
        if (envKey != null && !envKey.isBlank()) {
            byte[] keyBytes = Base64.getDecoder().decode(envKey.strip());
            if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
                throw new IllegalStateException(KEY_ENV_NAME + " 必须是 Base64 编码的 32 字节密钥");
            }
            return new SecretKeySpec(keyBytes, "AES");
        }
        return loadOrCreateFileKey(keyFilePath());
    }

    private static Path keyFilePath() {
        String configuredPath = System.getenv(KEY_FILE_ENV_NAME);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Path.of(configuredPath.strip());
        }
        return Path.of(System.getProperty("user.home"), ".aimv", "credential.key");
    }

    private static SecretKey loadOrCreateFileKey(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                byte[] keyBytes = Base64.getDecoder().decode(Files.readString(keyFile).strip());
                if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
                    throw new IllegalStateException("密钥文件内容无效: " + keyFile);
                }
                return new SecretKeySpec(keyBytes, "AES");
            }
            byte[] keyBytes = new byte[AES_KEY_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(keyBytes);
            Files.createDirectories(keyFile.getParent());
            Path temp = Files.createTempFile(keyFile.getParent(), "credential", ".key.tmp");
            Files.writeString(temp, Base64.getEncoder().encodeToString(keyBytes));
            try {
                Files.setPosixFilePermissions(temp,
                    Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // 非 POSIX 文件系统（如 Windows）跳过权限收紧
            }
            Files.move(temp, keyFile, StandardCopyOption.ATOMIC_MOVE);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取或创建 API key 加密密钥文件: " + keyFile, exception);
        }
    }
}
