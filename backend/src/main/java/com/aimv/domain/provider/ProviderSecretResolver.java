package com.aimv.domain.provider;

import java.util.Optional;

public interface ProviderSecretResolver {

    Optional<String> resolve(String apiKeyId);

    static ProviderSecretResolver empty() {
        return apiKeyId -> Optional.empty();
    }
}
