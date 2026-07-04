package com.aimv.application.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ApiKeyMasker {

    private ApiKeyMasker() {
    }

    static String mask(String apiKey) {
        if (apiKey == null || apiKey.length() < 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
    }

    static String hash(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
