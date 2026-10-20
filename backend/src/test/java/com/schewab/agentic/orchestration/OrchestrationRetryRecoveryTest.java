package com.schwab.agentic.orchestration;

import com.schwab.agentic.orchestration.application.OrchestrationEngine;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.OrchestrationRunRepository;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageInstanceRepository;
import com.schwab.agentic.orchestration.domain.StageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.orchestration.max-retries=3",
        "app.orchestration.testing-fail-until-attempt=2"
})
@ActiveProfiles("test")
class OrchestrationRetryRecoveryTest {

    @Autowired
    private OrchestrationEngine orchestrationEngine;

    @Autowired
    private OrchestrationRunRepository runRepository;

    @Autowired
    private StageInstanceRepository stageInstanceRepository;

    @Test
    void test_testing_recovers_after_injected_failures_and_reaches_release_gate() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Recover from flaky tests",
                "Build a URL shortener with analytics and reliability features"
        );

        // Act
        orchestrationEngine.advance(run.getId());

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.AWAITING_APPROVAL);
        assertThat(current.getRetryCount()).isGreaterThanOrEqualTo(1);
        StageInstance release = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL)
                .getFirst();
        assertThat(release.getStageKey()).isEqualTo("release");
    }
}
