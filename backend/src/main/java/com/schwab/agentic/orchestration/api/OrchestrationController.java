package com.schwab.agentic.orchestration.api;

import com.schwab.agentic.orchestration.application.AuditService;
import com.schwab.agentic.orchestration.application.OrchestrationEngine;
import com.schwab.agentic.orchestration.application.OrchestrationMetrics;
import com.schwab.agentic.orchestration.domain.AuditEvent;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.OrchestrationRunRepository;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageInstanceRepository;
import com.schwab.agentic.shared.web.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/orchestration")
public class OrchestrationController {

  private final OrchestrationEngine orchestrationEngine;
  private final OrchestrationRunRepository runRepository;
  private final StageInstanceRepository stageRepository;
  private final AuditService auditService;
  private final OrchestrationMetrics orchestrationMetrics;

  public OrchestrationController(
      OrchestrationEngine orchestrationEngine,
      OrchestrationRunRepository runRepository,
      StageInstanceRepository stageRepository,
      AuditService auditService,
      OrchestrationMetrics orchestrationMetrics
  ) {
    this.orchestrationEngine = orchestrationEngine;
    this.runRepository = runRepository;
    this.stageRepository = stageRepository;
    this.auditService = auditService;
    this.orchestrationMetrics = orchestrationMetrics;
  }

  @PostMapping("/runs")
  public ResponseEntity<OrchestrationDtos.RunResponse> start(@Valid @RequestBody OrchestrationDtos.StartRunRequest request) {
    OrchestrationRun run = orchestrationEngine.startRun(
        request.scenario(),
        request.title(),
        request.requirementText()
    );
    orchestrationEngine.executeAsync(run.getId());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(toRunResponse(run.getId()));
  }

  @GetMapping("/runs")
  public List<OrchestrationDtos.RunResponse> list() {
    return runRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(run -> toRunResponse(run.getId()))
        .toList();
  }

  @GetMapping("/runs/{runId}")
  public OrchestrationDtos.RunResponse get(@PathVariable String runId) {
    return toRunResponse(runId);
  }

  @GetMapping("/runs/{runId}/audit")
  public List<OrchestrationDtos.AuditResponse> audit(@PathVariable String runId) {
    requireRun(runId);
    return auditService.lineage(runId).stream().map(this::toAudit).toList();
  }

  @PostMapping("/runs/{runId}/stages/{stageId}/decision")
  public OrchestrationDtos.RunResponse decide(
      @PathVariable String runId,
      @PathVariable String stageId,
      @Valid @RequestBody OrchestrationDtos.GateDecisionRequest request
  ) {
    orchestrationEngine.decideGate(runId, stageId, Boolean.TRUE.equals(request.approved()),
        request.decidedBy(), request.reason());
    return toRunResponse(runId);
  }

  @PostMapping("/runs/{runId}/safe-stop")
  public OrchestrationDtos.RunResponse safeStop(
      @PathVariable String runId,
      @Valid @RequestBody OrchestrationDtos.ActorRequest request
  ) {
    orchestrationEngine.safeStop(runId, request.actor());
    return toRunResponse(runId);
  }

  @PostMapping("/runs/{runId}/rollback")
  public OrchestrationDtos.RunResponse rollback(
      @PathVariable String runId,
      @Valid @RequestBody OrchestrationDtos.ActorRequest request
  ) {
    orchestrationEngine.rollback(runId, request.actor());
    return toRunResponse(runId);
  }

  @PostMapping("/runs/{runId}/replan")
  public OrchestrationDtos.RunResponse replan(
      @PathVariable String runId,
      @Valid @RequestBody OrchestrationDtos.ReplanRequest request
  ) {
    orchestrationEngine.replan(runId, request.updatedRequirement(), request.actor());
    return toRunResponse(runId);
  }

  @GetMapping("/metrics")
  public OrchestrationDtos.MetricsResponse metrics() {
    OrchestrationMetrics.MetricsSnapshot snapshot = orchestrationMetrics.snapshot();
    Map<String, Long> byStatus = snapshot.runsByStatus().entrySet().stream()
        .collect(Collectors.toMap(
            e -> e.getKey().name(),
            Map.Entry::getValue,
            (a, b) -> a,
            LinkedHashMap::new
        ));
    return new OrchestrationDtos.MetricsResponse(
        snapshot.successRate(),
        snapshot.retryCount(),
        snapshot.rollbackCount(),
        snapshot.mttrMillis(),
        snapshot.meanLatencyMillis(),
        byStatus
    );
  }

  @GetMapping("/runs/{runId}/pending-approvals")
  public List<OrchestrationDtos.StageResponse> pendingApprovals(@PathVariable String runId) {
    requireRun(runId);
    return stageRepository.findByRunIdOrderBySequenceOrderAsc(runId).stream()
        .filter(s -> s.getStatus().name().equals("AWAITING_APPROVAL"))
        .map(this::toStage)
        .toList();
  }

  private OrchestrationDtos.RunResponse toRunResponse(String runId) {
    OrchestrationRun run = requireRun(runId);
    List<OrchestrationDtos.StageResponse> stages = stageRepository.findByRunIdOrderBySequenceOrderAsc(runId)
        .stream()
        .map(this::toStage)
        .toList();
    return new OrchestrationDtos.RunResponse(
        run.getId(),
        run.getScenario(),
        run.getTitle(),
        run.getRequirementText(),
        run.getStatus(),
        run.getAssumptionsJson(),
        run.getSummaryMarkdown(),
        run.getCreatedAt(),
        run.getStartedAt(),
        run.getCompletedAt(),
        run.getRetryCount(),
        run.getRollbackCount(),
        stages
    );
  }

  private OrchestrationDtos.StageResponse toStage(StageInstance stage) {
    return new OrchestrationDtos.StageResponse(
        stage.getId(),
        stage.getStageType(),
        stage.getStageKey(),
        stage.getStatus(),
        stage.getSequenceOrder(),
        stage.isRequiresApproval(),
        stage.isParallelGroup(),
        stage.getDependsOn(),
        stage.getOutputJson(),
        stage.getArtifactPath(),
        stage.getAttempt()
    );
  }

  private OrchestrationDtos.AuditResponse toAudit(AuditEvent event) {
    return new OrchestrationDtos.AuditResponse(
        event.getId(),
        event.getStageKey(),
        event.getEventType(),
        event.getActor(),
        event.getDetailJson(),
        event.getOccurredAt()
    );
  }

  private OrchestrationRun requireRun(String runId) {
    return runRepository.findById(runId).orElseThrow(() -> new NotFoundException("Run not found"));
  }
}
