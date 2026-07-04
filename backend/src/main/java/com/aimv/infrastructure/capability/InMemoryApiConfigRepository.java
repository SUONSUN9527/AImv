package com.aimv.infrastructure.capability;

import com.aimv.domain.capability.ApiConfigRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.ApiSelectionSnapshot;
import com.aimv.domain.shared.ChainType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryApiConfigRepository implements ApiConfigRepository {

    private final Map<String, ApiCredential> credentials = new ConcurrentHashMap<>();
    private final Map<String, ApiSelectionSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public ApiCredential save(ApiCredential credential) {
        credentials.put(credential.apiKeyId(), credential);
        return credential;
    }

    @Override
    public Optional<ApiCredential> findCredential(String apiKeyId) {
        return Optional.ofNullable(credentials.get(apiKeyId));
    }

    @Override
    public List<ApiCredential> findCredentials(ChainType chainType, String capabilityType) {
        return credentials.values().stream()
            .filter(credential -> credential.chainType() == chainType)
            .filter(credential -> credential.capabilityType().equals(capabilityType))
            .filter(credential -> credential.status() != ApiKeyStatus.DELETED)
            .toList();
    }

    @Override
    public List<ApiCredential> findSelectedCredentials(ChainType chainType) {
        return credentials.values().stream()
            .filter(credential -> credential.chainType() == chainType)
            .filter(ApiCredential::selected)
            .filter(credential -> credential.status() == ApiKeyStatus.ACTIVE)
            .toList();
    }

    @Override
    public void saveSnapshot(ApiSelectionSnapshot snapshot) {
        snapshots.put(snapshot.snapshotId(), snapshot);
    }

    @Override
    public List<ApiSelectionSnapshot> findSnapshots(String chainRunId) {
        return snapshots.values().stream()
            .filter(snapshot -> snapshot.chainRunId().equals(chainRunId))
            .toList();
    }
}
