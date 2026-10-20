package com.schwab.agentic.orchestration;

import tools.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageType;
import com.schwab.agentic.orchestration.policy.PolicyGuardrails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyGuardrailsTest {

    private PolicyGuardrails policyGuardrails;
    private OrchestrationRun ambiguousRun;
    private StageInstance releaseStage;

    @BeforeEach
    void setUp() {
        policyGuardrails = new PolicyGuardrails(new ObjectMapper());
        ambiguousRun = new OrchestrationRun(
                ScenarioType.AMBIGUOUS,
                "Enterprise",
                "Make reliable"
        );
        releaseStage = new StageInstance(
                ambiguousRun.getId(),
                StageType.RELEASE_READINESS,
                "release",
                60,
                true,
                false,
                "documentation"
        );
    }

    @Test
    void test_evaluate_exit_ambiguous_release_missing_gates_rejected() {
        // Arrange
        String output = "{\"releaseReady\":true}";

        // Act
        PolicyGuardrails.PolicyResult result = policyGuardrails.evaluateExit(releaseStage, ambiguousRun, output);

        // Assert
        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("gates"));
    }

    @Test
    void test_evaluate_exit_ambiguous_release_valid_measured_gates_allowed() {
        // Arrange
        String output = """
                {
                  "releaseReady": true,
                  "gates": {
                    "security": {"passed": true, "checks": ["open-redirect"]},
                    "compliance": {"passed": true, "criticalVulns": 0},
                    "slo": {
                      "passed": true,
                      "p99RedirectLatencyMs": 3.0,
                      "measured": true,
                      "evidenceSource": "live-redirect-probe"
                    }
                  }
                }
                """;

        // Act
        PolicyGuardrails.PolicyResult result = policyGuardrails.evaluateExit(releaseStage, ambiguousRun, output);

        // Assert
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void test_evaluate_exit_ambiguous_release_self_asserted_slo_rejected() {
        // Arrange — fixture-only latency without live probe evidence must fail
        String output = """
                {
                  "gates": {
                    "security": {"passed": true},
                    "compliance": {"passed": true, "criticalVulns": 0},
                    "slo": {"passed": true, "p99RedirectLatencyMs": 3.0}
                  }
                }
                """;

        // Act
        PolicyGuardrails.PolicyResult result = policyGuardrails.evaluateExit(releaseStage, ambiguousRun, output);

        // Assert
        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.toLowerCase().contains("live-redirect-probe")
            || v.toLowerCase().contains("measured"));
    }

    @Test
    void test_evaluate_exit_ambiguous_release_slo_latency_too_high_rejected() {
        // Arrange
        String output = """
                {
                  "gates": {
                    "security": {"passed": true},
                    "compliance": {"passed": true, "criticalVulns": 0},
                    "slo": {
                      "passed": true,
                      "p99RedirectLatencyMs": 25.0,
                      "measured": true,
                      "evidenceSource": "live-redirect-probe"
                    }
                  }
                }
                """;

        // Act
        PolicyGuardrails.PolicyResult result = policyGuardrails.evaluateExit(releaseStage, ambiguousRun, output);

        // Assert
        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.toLowerCase().contains("slo"));
    }

    @Test
    void test_evaluate_entry_rejects_secrets_in_requirement() {
        // Arrange
        OrchestrationRun run = new OrchestrationRun(
                ScenarioType.GREENFIELD,
                "Secret leak",
                "Build shortener password: hunter2"
        );
        StageInstance requirements = new StageInstance(
                run.getId(),
                StageType.REQUIREMENTS,
                "requirements",
                10,
                false,
                false,
                ""
        );

        // Act
        PolicyGuardrails.PolicyResult result = policyGuardrails.evaluateEntry(requirements, run);

        // Assert
        assertThat(result.allowed()).isFalse();
    }
}
