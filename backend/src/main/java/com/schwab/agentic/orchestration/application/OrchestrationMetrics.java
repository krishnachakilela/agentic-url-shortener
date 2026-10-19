package com.schwab.agentic.orchestration.application;

import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.OrchestrationRunRepository;
import com.schwab.agentic.orchestration.domain.RunStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrchestrationMetrics {

  private final Counter successCounter;
  private final Counter failureCounter;
  private final Counter retryCounter;
  private final Counter rollbackCounter;
  private final Timer endToEndLatency;
  private final AtomicLong mttrMillis = new AtomicLong(0);
  private final OrchestrationRunRepository runRepository;

  public OrchestrationMetrics(MeterRegistry registry, OrchestrationRunRepository runRepository) {
    this.runRepository = runRepository;
    this.successCounter = registry.counter("orchestration.run.success");
    this.failureCounter = registry.counter("orchestration.run.failure");
    this.retryCounter = registry.counter("orchestration.run.retry");
    this.rollbackCounter = registry.counter("orchestration.run.rollback");
    this.endToEndLatency = registry.timer("orchestration.run.latency");
    registry.gauge("orchestration.run.mttr.ms", mttrMillis);
  }

  public void recordSuccess(OrchestrationRun run) {
    successCounter.increment();
    recordLatency(run);
  }

  public void recordFailure(OrchestrationRun run) {
    failureCounter.increment();
    recordLatency(run);
    if (run.getStartedAt() != null && run.getCompletedAt() != null) {
      mttrMillis.set(Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis());
    }
  }

  public void recordRetry() {
    retryCounter.increment();
  }

  public void recordRollback() {
    rollbackCounter.increment();
  }

  private void recordLatency(OrchestrationRun run) {
    if (run.getStartedAt() == null || run.getCompletedAt() == null) {
      return;
    }
    long millis = Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis();
    endToEndLatency.record(millis, TimeUnit.MILLISECONDS);
  }

  public MetricsSnapshot snapshot() {
    Map<RunStatus, Long> byStatus = new EnumMap<>(RunStatus.class);
    for (RunStatus status : RunStatus.values()) {
      byStatus.put(status, 0L);
    }
    long totalRetries = 0;
    long totalRollbacks = 0;
    for (OrchestrationRun run : runRepository.findAll()) {
      byStatus.merge(run.getStatus(), 1L, Long::sum);
      totalRetries += run.getRetryCount();
      totalRollbacks += run.getRollbackCount();
    }
    double successRate = 0.0;
    long completed = byStatus.get(RunStatus.SUCCEEDED) + byStatus.get(RunStatus.FAILED)
        + byStatus.get(RunStatus.ROLLED_BACK) + byStatus.get(RunStatus.SAFE_STOPPED);
    if (completed > 0) {
      successRate = (double) byStatus.get(RunStatus.SUCCEEDED) / completed;
    }
    return new MetricsSnapshot(
        successRate,
        totalRetries,
        totalRollbacks,
        mttrMillis.get(),
        endToEndLatency.mean(TimeUnit.MILLISECONDS),
        byStatus
    );
  }

  public record MetricsSnapshot(
      double successRate,
      long retryCount,
      long rollbackCount,
      long mttrMillis,
      double meanLatencyMillis,
      Map<RunStatus, Long> runsByStatus
  ) {
  }
}
