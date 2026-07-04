package com.aimv.application.config;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.provider.ProviderSecretResolver;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ApiCredentialSecretResolver implements ProviderSecretResolver {

    private final ApiConfigRepository apiConfigRepository;

    public ApiCredentialSecretResolver(ApiConfigRepository apiConfigRepository) {
        this.apiConfigRepository = apiConfigRepository;
    }

    @Override
    public Optional<String> resolve(String apiKeyId) {
        if (apiKeyId == null || apiKeyId.isBlank()) {
            return Optional.empty();
        }
        return apiConfigRepository.findCredential(apiKeyId)
            .filter(credential -> credential.status() != ApiKeyStatus.DELETED)
            .map(ApiCredential::encryptedSecret)
            .filter(encryptedSecret -> encryptedSecret != null && !encryptedSecret.isBlank())
            .flatMap(ApiCredentialSecretResolver::tryDecrypt)
            .filter(secret -> !secret.isBlank());
    }

    /**
     * 解密失败（占位密钥、AES key 轮换不匹配等）时返回空，让上层回退到服务级配置 key 或匿名免费层，
     * 而不是抛异常让整条链路崩在 I00。
     */
    private static Optional<String> tryDecrypt(String encryptedSecret) {
        try {
            return Optional.of(ApiKeyProtector.decrypt(encryptedSecret));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
