package com.aimv.interfaces.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddApiKeyRequest(
        @NotBlank @Size(max = 64) String provider,
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 4096) String apiKey,
        @Size(max = 128) String model
) {
}
