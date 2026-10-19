package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageStatus;
import com.schwab.agentic.orchestration.domain.StageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OrchestrationDtos {

  private OrchestrationDtos() {
  }

  public record StartRunRequest(
      @NotNull ScenarioType scenario,
      @NotBlank @Size(min = 3, max = 200) String title,
      @NotBlank @Size(min = 10, max = 4000) String requirementText
  ) {
  }

  public record GateDecisionRequest(
      @NotBlank @Size(min = 1, max = 128) String decidedBy,
      @NotBlank @Size(min = 3, max = 1000) String reason,
      @NotNull Boolean approved
  ) {
  }

  public record ReplanRequest(
      @NotBlank @Size(min = 10, max = 4000) String updatedRequirement,
      @NotBlank @Size(min = 1, max = 128) String actor
  ) {
  }

  public record ActorRequest(
      @NotBlank @Size(min = 1, max = 128) String actor
  ) {
  }

  public record RunResponse(
      String id,
      ScenarioType scenario,
      String title,
      String requirementText,
      RunStatus status,
      String assumptionsJson,
      String summaryMarkdown,
      Instant createdAt,
      Instant startedAt,
      Instant completedAt,
      int retryCount,
      int rollbackCount,
      List<StageResponse> stages
  ) {
  }

  public record StageResponse(
      String id,
      StageType stageType,
      String stageKey,
      StageStatus status,
      int sequenceOrder,
      boolean requiresApproval,
      boolean parallelGroup,
      String dependsOn,
      String outputJson,
      String artifactPath,
      int attempt
  ) {
  }

  public record AuditResponse(
      String id,
      String stageKey,
      String eventType,
      String actor,
      String detailJson,
      Instant occurredAt
  ) {
  }

  public record MetricsResponse(
      double successRate,
      long retryCount,
      long rollbackCount,
      long mttrMillis,
      double meanLatencyMillis,
      Map<String, Long> runsByStatus
  ) {
  }
}
