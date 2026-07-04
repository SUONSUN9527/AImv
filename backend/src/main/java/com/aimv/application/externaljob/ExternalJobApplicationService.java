package com.aimv.application.externaljob;

import com.aimv.domain.chain.ChainRunRepository;
import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobRepository;
import com.aimv.shared.error.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExternalJobApplicationService {

    private final ChainRunRepository chainRunRepository;
    private final ExternalJobRepository externalJobRepository;

    public ExternalJobApplicationService(ChainRunRepository chainRunRepository,
            ExternalJobRepository externalJobRepository) {
        this.chainRunRepository = chainRunRepository;
        this.externalJobRepository = externalJobRepository;
    }

    public List<ExternalJob> listByChainRunId(String chainRunId) {
        chainRunRepository.findById(chainRunId)
            .orElseThrow(() -> new ResourceNotFoundException("链路运行不存在"));
        return externalJobRepository.findByChainRunId(chainRunId);
    }
}
