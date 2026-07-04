package com.aimv.domain.externaljob;

import java.util.List;
import java.util.Optional;

public interface ExternalJobRepository {

    ExternalJob save(ExternalJob externalJob);

    List<ExternalJob> findByChainRunId(String chainRunId);

    Optional<ExternalJob> findByProviderJobId(String provider, String providerJobId);
}
