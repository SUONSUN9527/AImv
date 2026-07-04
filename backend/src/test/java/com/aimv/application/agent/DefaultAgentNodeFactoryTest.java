package com.aimv.application.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.aimv.domain.agent.AgentDefinition;
import com.aimv.domain.agent.AgentNodeRun;
import com.aimv.domain.agent.AgentNodeRunRepository;
import com.aimv.domain.capability.ApiCredential;
import com.aimv.domain.capability.ApiKeyStatus;
import com.aimv.domain.capability.FreeModelGateStatus;
import com.aimv.domain.externaljob.ExternalJob;
import com.aimv.domain.externaljob.ExternalJobRepository;
import com.aimv.domain.externaljob.ExternalJobStatus;
import com.aimv.domain.provider.ProviderHttpResponse;
import com.aimv.domain.shared.ChainType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultAgentNodeFactoryTest {

    @Test
    void persistsExternalJobImmediatelyAfterProviderSubmissionBeforeAgentNodeRun() {
        List<String> writeOrder = new ArrayList<>();
        AtomicReference<ExternalJob> savedExternalJob = new AtomicReference<>();
        AgentNodeRunRepository agentNodeRunRepository = new OrderedAgentNodeRunRepository(writeOrder);
        ExternalJobRepository externalJobRepository = new OrderedExternalJobRepository(writeOrder,
            savedExternalJob);
        DefaultAgentNodeFactory factory = new DefaultAgentNodeFactory(agentNodeRunRepository,
            externalJobRepository, request -> new ProviderHttpResponse("provider-job-1", "SUCCEEDED",
                "adapter completed", List.of("artifact-1"), Map.of("durationMs", 120), "quota-ok"));

        factory.create(new AgentDefinition("I40Agent", "CANDIDATE_SELECTION", "image.generate.free"),
                "chain-1", "stage-1", "node-1")
            .execute(new AgentNodeInput("I40", "retrieval-1", credential(),
                Map.of("prompt", "城市悬疑封面")));

        assertThat(writeOrder).containsExactly("external-job", "agent-node-run");
        assertThat(savedExternalJob.get().providerJobId()).isEqualTo("provider-job-1");
        assertThat(savedExternalJob.get().status()).isEqualTo(ExternalJobStatus.SUCCEEDED);
        assertThat(savedExternalJob.get().retryPolicy()).isEqualTo("FREE_PROVIDER_RETRY_ONLY");
        assertThat(savedExternalJob.get().requestHash()).startsWith("sha256:");
        assertThat(savedExternalJob.get().responseMetadata().toString()).doesNotContain("secret-key-1234");
    }

    @Test
    void marksAgentNodeFailedWhenProviderReportsFreeQuotaExhausted() {
        AtomicReference<AgentNodeRun> savedAgentNodeRun = new AtomicReference<>();
        AtomicReference<ExternalJob> savedExternalJob = new AtomicReference<>();
        AgentNodeRunRepository agentNodeRunRepository = new CapturingAgentNodeRunRepository(savedAgentNodeRun);
        ExternalJobRepository externalJobRepository = new CapturingExternalJobRepository(savedExternalJob);
        DefaultAgentNodeFactory factory = new DefaultAgentNodeFactory(agentNodeRunRepository,
            externalJobRepository, request -> new ProviderHttpResponse("provider-job-quota", "FAILED",
                "free quota exhausted", List.of(), Map.of("rawErrorCode", "FREE_QUOTA_EXHAUSTED"),
                "free-quota-exhausted"));

        AgentNodeResult result = factory.create(new AgentDefinition("I40Agent", "CANDIDATE_SELECTION",
                    "image.generate.free"), "chain-1", "stage-1", "node-1")
            .execute(new AgentNodeInput("I40", "retrieval-1", credential(),
                Map.of("prompt", "城市悬疑封面")));

        assertThat(savedExternalJob.get().status()).isEqualTo(ExternalJobStatus.FAILED);
        assertThat(savedAgentNodeRun.get().status()).isEqualTo(com.aimv.domain.agent.AgentNodeRunStatus.FAILED);
        assertThat(result.nodeRun().status()).isEqualTo(com.aimv.domain.agent.AgentNodeRunStatus.FAILED);
        assertThat(savedAgentNodeRun.get().outputSummary()).contains("free quota exhausted");
        assertThat(savedExternalJob.get().responseMetadata().toString()).doesNotContain("secret-key-1234");
    }

    private ApiCredential credential() {
        return new ApiCredential("key-1", ChainType.IMAGE, "image.generate.free", "fixture-free",
            "fixture", "hash", "encrypted", "****1234", "fixture-contract", ApiKeyStatus.ACTIVE,
            true, null, FreeModelGateStatus.PASSED);
    }

    private static final class OrderedAgentNodeRunRepository implements AgentNodeRunRepository {

        private final List<String> writeOrder;

        private OrderedAgentNodeRunRepository(List<String> writeOrder) {
            this.writeOrder = writeOrder;
        }

        @Override
        public AgentNodeRun save(AgentNodeRun agentNodeRun) {
            writeOrder.add("agent-node-run");
            return agentNodeRun;
        }

        @Override
        public Optional<AgentNodeRun> findById(String nodeRunId) {
            return Optional.empty();
        }
    }

    private static final class OrderedExternalJobRepository implements ExternalJobRepository {

        private final List<String> writeOrder;
        private final AtomicReference<ExternalJob> savedExternalJob;

        private OrderedExternalJobRepository(List<String> writeOrder,
                AtomicReference<ExternalJob> savedExternalJob) {
            this.writeOrder = writeOrder;
            this.savedExternalJob = savedExternalJob;
        }

        @Override
        public ExternalJob save(ExternalJob externalJob) {
            writeOrder.add("external-job");
            savedExternalJob.set(externalJob);
            return externalJob;
        }

        @Override
        public List<ExternalJob> findByChainRunId(String chainRunId) {
            return List.of();
        }

        @Override
        public Optional<ExternalJob> findByProviderJobId(String provider, String providerJobId) {
            return Optional.empty();
        }
    }

    private static final class CapturingAgentNodeRunRepository implements AgentNodeRunRepository {

        private final AtomicReference<AgentNodeRun> savedAgentNodeRun;

        private CapturingAgentNodeRunRepository(AtomicReference<AgentNodeRun> savedAgentNodeRun) {
            this.savedAgentNodeRun = savedAgentNodeRun;
        }

        @Override
        public AgentNodeRun save(AgentNodeRun agentNodeRun) {
            savedAgentNodeRun.set(agentNodeRun);
            return agentNodeRun;
        }

        @Override
        public Optional<AgentNodeRun> findById(String nodeRunId) {
            return Optional.empty();
        }
    }

    private static final class CapturingExternalJobRepository implements ExternalJobRepository {

        private final AtomicReference<ExternalJob> savedExternalJob;

        private CapturingExternalJobRepository(AtomicReference<ExternalJob> savedExternalJob) {
            this.savedExternalJob = savedExternalJob;
        }

        @Override
        public ExternalJob save(ExternalJob externalJob) {
            savedExternalJob.set(externalJob);
            return externalJob;
        }

        @Override
        public List<ExternalJob> findByChainRunId(String chainRunId) {
            return List.of();
        }

        @Override
        public Optional<ExternalJob> findByProviderJobId(String provider, String providerJobId) {
            return Optional.empty();
        }
    }
}
