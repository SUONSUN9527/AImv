package com.aimv.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.shared.ChainType;
import org.junit.jupiter.api.Test;

class ApiKeyProtectorTest {

    @Test
    void encryptsApiKeyWithoutKeepingPlaintext() {
        String encrypted = ApiKeyProtector.encrypt("plain-secret-1234");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).doesNotContain("plain-secret-1234");
        assertThat(ApiKeyProtector.decrypt(encrypted)).isEqualTo("plain-secret-1234");
    }

    @Test
    void credentialStringDoesNotExposeSecretMaterials() {
        ApiCredential credential = new ApiCredential("key-1", ChainType.IMAGE, "llm.text.free",
            "fixture-free", "fixture", "secret-hash-value", "encrypted-secret-value", "****1234",
            "fixture-model", ApiKeyStatus.AVAILABLE, false, null, FreeModelGateStatus.PENDING_VERIFY);

        assertThat(credential.toString())
            .doesNotContain("secret-hash-value")
            .doesNotContain("encrypted-secret-value")
            .contains("maskedKey='****1234'");
    }
}
