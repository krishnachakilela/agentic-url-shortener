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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrchestrationEngineTest {

    @Autowired
    private OrchestrationEngine orchestrationEngine;

    @Autowired
    private OrchestrationRunRepository runRepository;

    @Autowired
    private StageInstanceRepository stageInstanceRepository;

    @Test
    void test_greenfield_run_reaches_release_approval_gate() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Greenfield shortener",
                "Build a URL shortener with analytics and reliability features"
        );

        // Act
        orchestrationEngine.advance(run.getId());

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.AWAITING_APPROVAL);
        List<StageInstance> awaiting = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL);
        assertThat(awaiting).hasSize(1);
        assertThat(awaiting.getFirst().getStageKey()).isEqualTo("release");
    }

    @Test
    void test_gate_rejection_marks_run_failed() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Reject release",
                "Build a URL shortener with analytics and reliability features"
        );
        orchestrationEngine.advance(run.getId());
        StageInstance release = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL)
                .getFirst();

        // Act
        orchestrationEngine.decideGate(run.getId(), release.getId(), false, "reviewer", "Needs more tests");

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.FAILED);
    }

    @Test
    void test_gate_approval_completes_run_successfully() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Approve release",
                "Build a URL shortener with analytics and reliability features"
        );
        orchestrationEngine.advance(run.getId());
        StageInstance release = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL)
                .getFirst();

        // Act
        orchestrationEngine.decideGate(run.getId(), release.getId(), true, "reviewer", "Looks good");

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(current.getSummaryMarkdown()).contains("Final Engineering Summary");
    }
}
