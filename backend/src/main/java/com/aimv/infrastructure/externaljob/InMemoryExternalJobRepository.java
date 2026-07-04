package com.aimv.infrastructure.externaljob;

import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!postgres")
public class InMemoryExternalJobRepository implements ExternalJobRepository {

    private final Map<String, ExternalJob> externalJobs = new ConcurrentHashMap<>();

    @Override
    public ExternalJob save(ExternalJob externalJob) {
        externalJobs.put(externalJob.externalJobId(), externalJob);
        return externalJob;
    }

    @Override
    public List<ExternalJob> findByChainRunId(String chainRunId) {
        return externalJobs.values().stream()
            .filter(externalJob -> externalJob.chainRunId().equals(chainRunId))
            .sorted(Comparator.comparing(ExternalJob::createdAt))
            .toList();
    }

    @Override
    public Optional<ExternalJob> findByProviderJobId(String provider, String providerJobId) {
        return externalJobs.values().stream()
            .filter(externalJob -> externalJob.provider().equals(provider))
            .filter(externalJob -> externalJob.providerJobId().equals(providerJobId))
            .findFirst();
    }
}
