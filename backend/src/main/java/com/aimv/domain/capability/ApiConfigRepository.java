package com.aimv.domain.capability;

import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Optional;

public interface ApiConfigRepository {

    ApiCredential save(ApiCredential credential);

    Optional<ApiCredential> findCredential(String apiKeyId);

    List<ApiCredential> findCredentials(ChainType chainType, String capabilityType);

    List<ApiCredential> findSelectedCredentials(ChainType chainType);

    void saveSnapshot(ApiSelectionSnapshot snapshot);

    List<ApiSelectionSnapshot> findSnapshots(String chainRunId);
}
