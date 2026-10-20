package com.schwab.agentic.orchestration.application;

import tools.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.agents.FixtureStageAgentRegistry;
import com.schwab.agentic.orchestration.agents.StageAgent;
import com.schwab.agentic.orchestration.domain.GateDecision;
import com.schwab.agentic.orchestration.domain.GateDecisionRepository;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.OrchestrationRunRepository;
import com.schwab.agentic.orchestration.domain.RunStatus;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageInstanceRepository;
import com.schwab.agentic.orchestration.domain.StageStatus;
import com.schwab.agentic.orchestration.policy.PolicyGuardrails;
import com.schwab.agentic.shared.config.AppProperties;
import com.schwab.agentic.shared.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class OrchestrationEngine {

  private static final Logger log = LoggerFactory.getLogger(OrchestrationEngine.class);
  private static final String ACTOR_ORCHESTRATOR = "orchestrator";
  private static final String ACTOR_SYSTEM = "system";
  private static final String ACTOR_POLICY = "policy";
  private static final String ACTOR_AGENT = "agent";

  private final OrchestrationRunRepository runRepository;
  private final StageInstanceRepository stageRepository;
  private final GateDecisionRepository gateDecisionRepository;
  private final DependencyGraphFactory graphFactory;
  private final FixtureStageAgentRegistry agentRegistry;
  private final PolicyGuardrails policyGuardrails;
  private final AuditService auditService;
  private final ArtifactStore artifactStore;
  private final OrchestrationMetrics metrics;
  private final AppProperties appProperties;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;
  private final TaskExecutor taskExecutor;

  public OrchestrationEngine(
      OrchestrationRunRepository runRepository,
      StageInstanceRepository stageRepository,
      GateDecisionRepository gateDecisionRepository,
      DependencyGraphFactory graphFactory,
      FixtureStageAgentRegistry agentRegistry,
      PolicyGuardrails policyGuardrails,
      AuditService auditService,
      ArtifactStore artifactStore,
      OrchestrationMetrics metrics,
      AppProperties appProperties,
      ObjectMapper objectMapper,
      PlatformTransactionManager transactionManager,
      TaskExecutor applicationTaskExecutor
  ) {
    this.runRepository = runRepository;
    this.stageRepository = stageRepository;
    this.gateDecisionRepository = gateDecisionRepository;
    this.graphFactory = graphFactory;
    this.agentRegistry = agentRegistry;
    this.policyGuardrails = policyGuardrails;
    this.auditService = auditService;
    this.artifactStore = artifactStore;
    this.metrics = metrics;
    this.appProperties = appProperties;
    this.objectMapper = objectMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.taskExecutor = applicationTaskExecutor;
  }

  public OrchestrationRun startRun(ScenarioType scenario, String title, String requirementText) {
    return transactionTemplate.execute(status -> {
      OrchestrationRun run = new OrchestrationRun(scenario, title, requirementText);
      if (scenario == ScenarioType.AMBIGUOUS) {
        run.updateAssumptions(toJson(List.of(
            "SLO target: 99.9% availability for redirect path",
            "Authn deferred; admin APIs localhost-only for prototype",
            "Single region deployment"
        )));
      }
      runRepository.save(run);
      List<StageInstance> stages = graphFactory.buildGraph(run.getId(), scenario);
      stageRepository.saveAll(stages);
      auditService.record(run.getId(), null, "RUN_CREATED", ACTOR_SYSTEM, toJson(Map.of(
          "scenario", scenario.name(),
          "title", title
      )));
      return run;
    });
  }

  public void executeAsync(String runId) {
    taskExecutor.execute(() -> {
      try {
        advance(runId);
      } catch (Exception ex) {
        log.error("Orchestration run failed runId={}", runId, ex);
        failRun(runId);
      }
    });
  }

  public void advance(String runId) {
    transactionTemplate.executeWithoutResult(status -> advanceInTransaction(runId));
  }

  private void advanceInTransaction(String runId) {
    OrchestrationRun run = requireRun(runId);
    if (run.getStatus() == RunStatus.SAFE_STOPPED
        || run.getStatus() == RunStatus.SUCCEEDED
        || run.getStatus() == RunStatus.ROLLED_BACK
        || run.getStatus() == RunStatus.FAILED) {
      return;
    }

    run.markRunning();
    runRepository.save(run);
    auditService.record(runId, null, "RUN_ADVANCED", ACTOR_ORCHESTRATOR, "{}");

    List<StageInstance> stages = stageRepository.findByRunIdOrderBySequenceOrderAsc(runId);
    markReadyStages(stages);

    List<StageInstance> executable = stages.stream()
        .filter(s -> s.getStatus() == StageStatus.READY)
        .toList();

    if (executable.isEmpty()) {
      if (allSucceeded(stages)) {
        completeSuccess(run, stages);
      }
      return;
    }

    List<StageInstance> parallelBatch = executable.stream()
        .filter(StageInstance::isParallelGroup)
        .toList();
    List<StageInstance> serialBatch = executable.stream()
        .filter(s -> !s.isParallelGroup())
        .toList();

    boolean paused = false;
    if (!parallelBatch.isEmpty()) {
      paused = !executeParallelBatch(run, parallelBatch, stages) || paused;
    }
    for (StageInstance stage : serialBatch) {
      boolean continueRun = executeStage(run, stage, stages, null);
      if (!continueRun) {
        paused = true;
      }
    }

    stages = stageRepository.findByRunIdOrderBySequenceOrderAsc(runId);
    run = requireRun(runId);
    if (run.getStatus() == RunStatus.ROLLED_BACK || run.getStatus() == RunStatus.FAILED) {
      return;
    }
    if (!stageRepository.findByRunIdAndStatus(runId, StageStatus.AWAITING_APPROVAL).isEmpty()) {
      return;
    }
    if (allSucceeded(stages)) {
      completeSuccess(run, stages);
      return;
    }
    boolean hasReadyRetry = stages.stream().anyMatch(s -> s.getStatus() == StageStatus.READY);
    if (!allTerminal(stages) || hasReadyRetry) {
      if (paused && !hasReadyRetry) {
        return;
      }
      advanceInTransaction(runId);
    }
  }

  private boolean executeParallelBatch(
      OrchestrationRun run,
      List<StageInstance> parallelStages,
      List<StageInstance> allStages
  ) {
    Map<String, String> inputs = new HashMap<>();
    for (StageInstance stage : parallelStages) {
      PolicyGuardrails.PolicyResult entry = policyGuardrails.evaluateEntry(stage, run);
      if (!entry.allowed()) {
        stage.fail(toJson(Map.of("policyViolations", entry.violations())));
        stageRepository.save(stage);
        auditService.record(run.getId(), stage.getStageKey(), "ENTRY_GATE_FAILED", ACTOR_POLICY, toJson(entry.violations()));
        return handleFailure(run, stage);
      }
      stage.markRunning();
      String input = buildUpstreamContext(allStages, stage);
      stage.setInputJson(input);
      stageRepository.save(stage);
      inputs.put(stage.getId(), input);
      auditService.record(run.getId(), stage.getStageKey(), "STAGE_STARTED", ACTOR_ORCHESTRATOR, input);
    }

    Map<String, StageAgent.AgentResult> results = new ConcurrentHashMap<>();
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (StageInstance stage : parallelStages) {
      String input = inputs.get(stage.getId());
      StageAgent agent = agentRegistry.resolve(stage.getStageType());
      futures.add(CompletableFuture.runAsync(
          () -> results.put(stage.getId(), agent.execute(run, stage, input)),
          taskExecutor
      ));
    }
    try {
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    } catch (CompletionException ex) {
      log.error("Parallel stage execution failed runId={}", run.getId(), ex);
      for (StageInstance stage : parallelStages) {
        if (!results.containsKey(stage.getId())) {
          stage.fail(toJson(Map.of("error", "parallel_execution_failed")));
          stageRepository.save(stage);
        }
      }
      return handleFailure(run, parallelStages.getFirst());
    }

    boolean paused = false;
    for (StageInstance stage : parallelStages) {
      StageAgent.AgentResult result = results.get(stage.getId());
      if (result == null) {
        stage.fail(toJson(Map.of("error", "missing_parallel_result")));
        stageRepository.save(stage);
        return handleFailure(run, stage);
      }
      boolean continueRun = applyAgentResult(run, stage, result);
      if (!continueRun) {
        paused = true;
      }
    }
    return !paused;
  }

  private boolean executeStage(
      OrchestrationRun run,
      StageInstance stage,
      List<StageInstance> allStages,
      StageAgent.AgentResult precomputed
  ) {
    PolicyGuardrails.PolicyResult entry = policyGuardrails.evaluateEntry(stage, run);
    if (!entry.allowed()) {
      stage.fail(toJson(Map.of("policyViolations", entry.violations())));
      stageRepository.save(stage);
      auditService.record(run.getId(), stage.getStageKey(), "ENTRY_GATE_FAILED", ACTOR_POLICY, toJson(entry.violations()));
      return handleFailure(run, stage);
    }

    stage.markRunning();
    stage.setInputJson(buildUpstreamContext(allStages, stage));
    stageRepository.save(stage);
    auditService.record(run.getId(), stage.getStageKey(), "STAGE_STARTED", ACTOR_ORCHESTRATOR, stage.getInputJson());

    StageAgent.AgentResult result = precomputed != null
        ? precomputed
        : agentRegistry.resolve(stage.getStageType()).execute(run, stage, stage.getInputJson());

    return applyAgentResult(run, stage, result);
  }

  private boolean applyAgentResult(OrchestrationRun run, StageInstance stage, StageAgent.AgentResult result) {
    if (!result.success()) {
      stage.fail(result.outputJson());
      stageRepository.save(stage);
      auditService.record(run.getId(), stage.getStageKey(), "STAGE_FAILED", ACTOR_AGENT, result.outputJson());
      return handleFailure(run, stage);
    }

    PolicyGuardrails.PolicyResult exit = policyGuardrails.evaluateExit(stage, run, result.outputJson());
    if (!exit.allowed()) {
      stage.fail(toJson(Map.of("policyViolations", exit.violations(), "output", result.outputJson())));
      stageRepository.save(stage);
      auditService.record(run.getId(), stage.getStageKey(), "EXIT_GATE_FAILED", ACTOR_POLICY, toJson(exit.violations()));
      return handleFailure(run, stage);
    }

    String artifactPath = null;
    if (result.artifactContent() != null && result.artifactFileName() != null) {
      artifactPath = artifactStore.write(run.getId(), result.artifactFileName(), result.artifactContent());
    }

    if (stage.isRequiresApproval()) {
      stage.proposeForApproval(result.outputJson(), artifactPath);
      stageRepository.save(stage);
      run.awaitApproval();
      runRepository.save(run);
      auditService.record(run.getId(), stage.getStageKey(), "AWAITING_APPROVAL", ACTOR_ORCHESTRATOR, result.outputJson());
      return false;
    }

    stage.succeed(result.outputJson(), artifactPath);
    stageRepository.save(stage);
    mergeContext(run, stage.getStageKey(), result.outputJson());
    auditService.record(run.getId(), stage.getStageKey(), "STAGE_SUCCEEDED", ACTOR_AGENT, result.outputJson());
    return true;
  }

  public OrchestrationRun decideGate(String runId, String stageId, boolean approved, String decidedBy, String reason) {
    OrchestrationRun result = transactionTemplate.execute(status -> {
      OrchestrationRun run = requireRun(runId);
      StageInstance stage = stageRepository.findById(stageId)
          .orElseThrow(() -> new NotFoundException("Stage not found"));
      if (!stage.getRunId().equals(runId)) {
        throw new IllegalArgumentException("Stage does not belong to run");
      }
      if (stage.getStatus() != StageStatus.AWAITING_APPROVAL) {
        throw new IllegalStateException("Stage is not awaiting approval");
      }

      gateDecisionRepository.save(new GateDecision(runId, stageId, approved, decidedBy, reason));
      auditService.record(runId, stage.getStageKey(), approved ? "GATE_APPROVED" : "GATE_REJECTED", decidedBy,
          toJson(Map.of("reason", reason == null ? "" : reason)));

      if (!approved) {
        stage.fail(toJson(Map.of("rejected", true, "reason", reason)));
        stageRepository.save(stage);
        run.markFailed();
        runRepository.save(run);
        metrics.recordFailure(run);
        return run;
      }

      stage.succeed(stage.getOutputJson(), stage.getArtifactPath());
      stageRepository.save(stage);
      mergeContext(run, stage.getStageKey(), stage.getOutputJson());
      run.markRunning();
      runRepository.save(run);
      return run;
    });

    if (result != null && result.getStatus() == RunStatus.RUNNING) {
      advance(runId);
    }
    return requireRun(runId);
  }

  public OrchestrationRun safeStop(String runId, String actor) {
    return transactionTemplate.execute(status -> {
      OrchestrationRun run = requireRun(runId);
      run.safeStop();
      runRepository.save(run);
      auditService.record(runId, null, "SAFE_STOP", actor, "{}");
      return run;
    });
  }

  public OrchestrationRun rollback(String runId, String actor) {
    return transactionTemplate.execute(status -> performRollback(requireRun(runId), actor, "ROLLBACK"));
  }

  public OrchestrationRun replan(String runId, String updatedRequirement, String actor) {
    transactionTemplate.executeWithoutResult(status -> {
      OrchestrationRun run = requireRun(runId);
      run.replan();
      run.updateContext(toJson(Map.of(
          "previousContext", run.getContextJson(),
          "updatedRequirement", updatedRequirement
      )));
      runRepository.save(run);

      List<StageInstance> stages = stageRepository.findByRunIdOrderBySequenceOrderAsc(runId);
      for (StageInstance stage : stages) {
        if (stage.getStatus() != StageStatus.SUCCEEDED) {
          stage.skip();
          stageRepository.save(stage);
        }
      }

      List<StageInstance> newGraph = graphFactory.buildGraph(runId, run.getScenario());
      Set<String> succeededKeys = stages.stream()
          .filter(s -> s.getStatus() == StageStatus.SUCCEEDED)
          .map(StageInstance::getStageKey)
          .collect(Collectors.toSet());

      for (StageInstance candidate : newGraph) {
        if (!succeededKeys.contains(candidate.getStageKey())) {
          stageRepository.save(candidate);
        }
      }

      auditService.record(runId, null, "REPLAN", actor, toJson(Map.of("updatedRequirement", updatedRequirement)));
      run.markRunning();
      runRepository.save(run);
    });
    advance(runId);
    return requireRun(runId);
  }

  private boolean handleFailure(OrchestrationRun run, StageInstance stage) {
    mergeContext(run, "lastFailure", toJson(Map.of(
        "stageKey", stage.getStageKey(),
        "attempt", stage.getAttempt(),
        "output", stage.getOutputJson() == null ? "" : stage.getOutputJson()
    )));

    int maxRetries = appProperties.orchestration().maxRetries();
    if (stage.getAttempt() <= maxRetries) {
      run.incrementRetry();
      runRepository.save(run);
      metrics.recordRetry();
      auditService.record(run.getId(), stage.getStageKey(), "RETRY_SCHEDULED", ACTOR_ORCHESTRATOR,
          toJson(Map.of(
              "attempt", stage.getAttempt(),
              "maxRetries", maxRetries,
              "failureFeedback", stage.getOutputJson() == null ? "" : stage.getOutputJson()
          )));
      // Re-queue without recursive advance so parallel siblings are not re-entered mid-batch.
      stage.markReady();
      stageRepository.save(stage);
      return false;
    }

    auditService.record(run.getId(), stage.getStageKey(), "RETRY_EXHAUSTED", ACTOR_ORCHESTRATOR,
        toJson(Map.of("attempt", stage.getAttempt(), "maxRetries", maxRetries)));
    performRollback(run, ACTOR_ORCHESTRATOR, "AUTO_ROLLBACK_ON_RETRY_EXHAUSTION");
    auditService.record(run.getId(), stage.getStageKey(), "AWAITING_HUMAN_AFTER_ROLLBACK", ACTOR_ORCHESTRATOR,
        toJson(Map.of("hint", "Use replan or inspect audit lineage before continuing")));
    return false;
  }

  private OrchestrationRun performRollback(OrchestrationRun run, String actor, String eventType) {
    List<StageInstance> stages = stageRepository.findByRunIdOrderBySequenceOrderAsc(run.getId());
    for (StageInstance stage : stages) {
      if (stage.getStatus() == StageStatus.SUCCEEDED
          || stage.getStatus() == StageStatus.AWAITING_APPROVAL
          || stage.getStatus() == StageStatus.FAILED
          || stage.getStatus() == StageStatus.READY
          || stage.getStatus() == StageStatus.RUNNING) {
        stage.rollback();
        stageRepository.save(stage);
      }
    }
    run.markRolledBack();
    runRepository.save(run);
    metrics.recordRollback();
    auditService.record(run.getId(), null, eventType, actor, "{}");
    return run;
  }

  private void failRun(String runId) {
    transactionTemplate.executeWithoutResult(status ->
        runRepository.findById(runId).ifPresent(run -> {
          run.markFailed();
          runRepository.save(run);
          metrics.recordFailure(run);
          auditService.record(runId, null, "RUN_FAILED", ACTOR_ORCHESTRATOR,
              toJson(Map.of("detail", "internal_error")));
        }));
  }

  private void completeSuccess(OrchestrationRun run, List<StageInstance> stages) {
    String summary = buildSummary(run, stages);
    String path = artifactStore.write(run.getId(), "engineering-summary.md", summary);
    run.markSucceeded(summary);
    runRepository.save(run);
    metrics.recordSuccess(run);
    auditService.record(run.getId(), null, "RUN_SUCCEEDED", ACTOR_ORCHESTRATOR,
        toJson(Map.of("summaryPath", path)));
  }

  private String buildSummary(OrchestrationRun run, List<StageInstance> stages) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Final Engineering Summary\n\n");
    sb.append("## Scenario\n").append(run.getScenario()).append("\n\n");
    sb.append("## Requirement\n").append(run.getRequirementText()).append("\n\n");
    sb.append("## Assumptions\n").append(run.getAssumptionsJson()).append("\n\n");
    sb.append("## Stages\n");
    for (StageInstance stage : stages) {
      sb.append("- ").append(stage.getStageKey()).append(": ").append(stage.getStatus()).append("\n");
    }
    sb.append("\n## Risks / Trade-offs\n");
    sb.append("- Fixture agents are deterministic; live LLM mode needs stronger output validation.\n");
    sb.append("- Human approval is mandatory for release-class actions.\n");
    sb.append("- Retry exhaustion triggers automated rollback and pauses for human replan.\n");
    sb.append("\n## Validation\n");
    sb.append("- Entry/exit policy gates enforced.\n");
    sb.append("- Ambiguous releases require security, compliance, and SLO gate evidence.\n");
    sb.append("- Audit lineage persisted for every transition.\n");
    return sb.toString();
  }

  private void markReadyStages(List<StageInstance> stages) {
    Map<String, StageInstance> byKey = stages.stream()
        .collect(Collectors.toMap(StageInstance::getStageKey, s -> s, (a, b) -> a));
    for (StageInstance stage : stages) {
      if (stage.getStatus() != StageStatus.PENDING && stage.getStatus() != StageStatus.READY) {
        continue;
      }
      if (dependenciesMet(stage, byKey)) {
        stage.markReady();
        stageRepository.save(stage);
      }
    }
  }

  private boolean dependenciesMet(StageInstance stage, Map<String, StageInstance> byKey) {
    if (stage.getDependsOn() == null || stage.getDependsOn().isBlank()) {
      return true;
    }
    return Arrays.stream(stage.getDependsOn().split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .allMatch(dep -> {
          StageInstance dependency = byKey.get(dep);
          return dependency != null && dependency.getStatus() == StageStatus.SUCCEEDED;
        });
  }

  private String buildUpstreamContext(List<StageInstance> stages, StageInstance stage) {
    Map<String, Object> context = new HashMap<>();
    if (stage.getDependsOn() != null) {
      for (String dep : stage.getDependsOn().split(",")) {
        String key = dep.trim();
        if (key.isEmpty()) {
          continue;
        }
        stages.stream()
            .filter(s -> s.getStageKey().equals(key))
            .findFirst()
            .ifPresent(s -> context.put(key, s.getOutputJson()));
      }
    }
    if (stage.getAttempt() > 0 && stage.getOutputJson() != null && !stage.getOutputJson().isBlank()) {
      context.put("lastFailure", stage.getOutputJson());
      context.put("retryAttempt", stage.getAttempt());
    }
    return toJson(context);
  }

  private void mergeContext(OrchestrationRun run, String stageKey, String outputJson) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> context = objectMapper.readValue(
          run.getContextJson() == null ? "{}" : run.getContextJson(),
          Map.class
      );
      context.put(stageKey, objectMapper.readValue(outputJson, Object.class));
      run.updateContext(objectMapper.writeValueAsString(context));
      runRepository.save(run);
    } catch (Exception ex) {
      run.updateContext(toJson(Map.of(stageKey, outputJson)));
      runRepository.save(run);
    }
  }

  private boolean allSucceeded(List<StageInstance> stages) {
    return stages.stream().allMatch(s -> s.getStatus() == StageStatus.SUCCEEDED
        || s.getStatus() == StageStatus.SKIPPED);
  }

  private boolean allTerminal(List<StageInstance> stages) {
    return stages.stream().allMatch(s -> s.getStatus() == StageStatus.SUCCEEDED
        || s.getStatus() == StageStatus.FAILED
        || s.getStatus() == StageStatus.SKIPPED
        || s.getStatus() == StageStatus.ROLLED_BACK
        || s.getStatus() == StageStatus.AWAITING_APPROVAL);
  }

  private OrchestrationRun requireRun(String runId) {
    return runRepository.findById(runId).orElseThrow(() -> new NotFoundException("Run not found"));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{}";
    }
  }
}
