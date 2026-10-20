package com.schwab.agentic.orchestration;

import com.schwab.agentic.orchestration.application.RedirectLatencyProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedirectLatencyProbeTest {

  @Autowired
  private RedirectLatencyProbe redirectLatencyProbe;

  @Test
  void test_measure_creates_short_url_and_returns_live_probe_evidence() {
    // Arrange / Act
    RedirectLatencyProbe.ProbeResult result = redirectLatencyProbe.measure();

    // Assert
    assertThat(result.evidenceSource()).isEqualTo(RedirectLatencyProbe.EVIDENCE_SOURCE);
    assertThat(result.samples()).isGreaterThanOrEqualTo(3);
    assertThat(result.shortCode()).isNotBlank();
    assertThat(result.p99RedirectLatencyMs()).isGreaterThanOrEqualTo(0.0);
    assertThat(result.passed()).isTrue();
  }
}
