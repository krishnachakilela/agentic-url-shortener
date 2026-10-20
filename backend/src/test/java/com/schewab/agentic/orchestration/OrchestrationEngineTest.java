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

    @Test
    void test_brownfield_run_pauses_on_implement_api_change_control_gate() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.BROWNFIELD,
                "Add expiration",
                "Add link expiration and analytics export"
        );

        // Act
        orchestrationEngine.advance(run.getId());

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.AWAITING_APPROVAL);
        StageInstance gated = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL)
                .getFirst();
        assertThat(gated.getStageKey()).isEqualTo("implement-api");
    }

    @Test
    void test_ambiguous_run_records_assumptions_and_reaches_release_gate() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.AMBIGUOUS,
                "Enterprise readiness",
                "Make the shortener more reliable and enterprise ready"
        );

        // Act
        orchestrationEngine.advance(run.getId());
        OrchestrationRun afterImplementGate = runRepository.findById(run.getId()).orElseThrow();
        assertThat(afterImplementGate.getAssumptionsJson()).contains("99.9%");
        StageInstance implementApi = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL)
                .getFirst();
        assertThat(implementApi.getStageKey()).isEqualTo("implement-api");
        orchestrationEngine.decideGate(run.getId(), implementApi.getId(), true, "reviewer", "Assumptions accepted");

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.AWAITING_APPROVAL);
        StageInstance release = stageInstanceRepository.findByRunIdAndStatus(run.getId(), StageStatus.AWAITING_APPROVAL)
                .getFirst();
        assertThat(release.getStageKey()).isEqualTo("release");
        assertThat(release.getOutputJson()).contains("gates");
        assertThat(release.getOutputJson()).contains("security");
    }

    @Test
    void test_safe_stop_halts_running_workflow() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Stop me",
                "Build a URL shortener with analytics and reliability features"
        );
        orchestrationEngine.advance(run.getId());

        // Act
        orchestrationEngine.safeStop(run.getId(), "reviewer");

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.SAFE_STOPPED);
    }

    @Test
    void test_manual_rollback_marks_stages_rolled_back() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Rollback me",
                "Build a URL shortener with analytics and reliability features"
        );
        orchestrationEngine.advance(run.getId());

        // Act
        orchestrationEngine.rollback(run.getId(), "reviewer");

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.ROLLED_BACK);
        assertThat(current.getRollbackCount()).isGreaterThanOrEqualTo(1);
        List<StageInstance> stages = stageInstanceRepository.findByRunIdOrderBySequenceOrderAsc(run.getId());
        assertThat(stages).allMatch(s ->
                s.getStatus() == StageStatus.ROLLED_BACK || s.getStatus() == StageStatus.PENDING);
    }
}
