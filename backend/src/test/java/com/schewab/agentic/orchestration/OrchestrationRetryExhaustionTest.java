package com.schwab.agentic.orchestration;

import com.schwab.agentic.orchestration.application.AuditService;
import com.schwab.agentic.orchestration.application.OrchestrationEngine;
import com.schwab.agentic.orchestration.domain.AuditEvent;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.OrchestrationRunRepository;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.orchestration.max-retries=1",
        "app.orchestration.testing-fail-until-attempt=10"
})
@ActiveProfiles("test")
class OrchestrationRetryExhaustionTest {

    @Autowired
    private OrchestrationEngine orchestrationEngine;

    @Autowired
    private OrchestrationRunRepository runRepository;

    @Autowired
    private AuditService auditService;

    @Test
    void test_testing_failure_exhausts_retries_and_auto_rolls_back() {
        // Arrange
        OrchestrationRun run = orchestrationEngine.startRun(
                ScenarioType.GREENFIELD,
                "Force testing failures",
                "Build a URL shortener with analytics and reliability features"
        );

        // Act
        orchestrationEngine.advance(run.getId());

        // Assert
        OrchestrationRun current = runRepository.findById(run.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RunStatus.ROLLED_BACK);
        assertThat(current.getRetryCount()).isGreaterThanOrEqualTo(1);
        assertThat(current.getRollbackCount()).isGreaterThanOrEqualTo(1);

        List<AuditEvent> lineage = auditService.lineage(run.getId());
        assertThat(lineage.stream().map(AuditEvent::getEventType).toList())
                .contains("RETRY_SCHEDULED", "RETRY_EXHAUSTED", "AUTO_ROLLBACK_ON_RETRY_EXHAUSTION");
    }
}
