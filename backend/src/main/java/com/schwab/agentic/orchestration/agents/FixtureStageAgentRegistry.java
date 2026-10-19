package com.schwab.agentic.orchestration.agents;

import tools.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic fixture agents for offline demos (no live LLM required).
 * Swap AGENT_MODE later for Spring AI / LangChain4j without changing the orchestrator.
 */
@Component
public class FixtureStageAgentRegistry {

  private final ObjectMapper objectMapper;

  public FixtureStageAgentRegistry(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public StageAgent resolve(StageType stageType) {
    return switch (stageType) {
      case REQUIREMENTS -> this::requirements;
      case ARCHITECTURE -> this::architecture;
      case IMPLEMENT_API -> this::implementApi;
      case IMPLEMENT_UI -> this::implementUi;
      case TESTING -> this::testing;
      case DOCUMENTATION -> this::documentation;
      case RELEASE_READINESS -> this::release;
    };
  }

  private StageAgent.AgentResult requirements(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("intent", normalizeIntent(run));
    payload.put("ambiguities", ambiguities(run));
    payload.put("normalizedProblem", normalizedProblem(run));
    payload.put("tasks", List.of(
        "Define API contracts",
        "Implement persistence",
        "Add analytics",
        "Add reliability controls",
        "Document and gate release"
    ));
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# Requirements\n\n" + payload.get("normalizedProblem") + "\n",
        "requirements.md"
    );
  }

  private StageAgent.AgentResult architecture(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("style", "Modular monolith (DDD-ish)");
    payload.put("boundedContexts", List.of("urlshortener", "orchestration"));
    payload.put("impactedModules", impactedModules(run.getScenario()));
    payload.put("dataFlows", List.of(
        "Client -> Shortener API -> Postgres",
        "Redirect -> ClickEvent + analytics",
        "Angular -> Orchestration gates -> Agents"
    ));
    payload.put("upstream", upstream);
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# Architecture\n\nBounded contexts: urlshortener, orchestration\nScenario: " + run.getScenario() + "\n",
        "architecture.md"
    );
  }

  private StageAgent.AgentResult implementApi(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("changes", apiChanges(run.getScenario()));
    payload.put("openapi", List.of(
        "POST /api/v1/urls",
        "GET /api/v1/urls/{code}/analytics",
        "GET /api/v1/urls/{code}/analytics/export",
        "GET /r/{code}"
    ));
    payload.put("testsPlanned", List.of("create_success", "create_invalid_url", "redirect_tracks_click"));
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "// Proposed API delta for " + run.getScenario() + "\n" + String.join("\n", apiChanges(run.getScenario())),
        "implement-api.diff.txt"
    );
  }

  private StageAgent.AgentResult implementUi(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("screens", List.of("CreateShortUrl", "Analytics", "ApprovalConsole", "RunViewer"));
    payload.put("notes", "Angular HITL console for gated stages");
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# UI Implementation Notes\n\nApproval console + shortener admin for scenario " + run.getScenario() + "\n",
        "implement-ui.md"
    );
  }

  private StageAgent.AgentResult testing(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("unit", "passed");
    payload.put("integration", "passed");
    payload.put("coverageTarget", ">90% critical paths");
    payload.put("risks", List.of("LLM non-determinism mitigated by fixture mode"));
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# Test Report\n\nUnit: passed\nIntegration: passed\n",
        "test-report.md"
    );
  }

  private StageAgent.AgentResult documentation(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("readmeUpdated", true);
    payload.put("runbook", "Safe-stop, rollback, and approval gates documented");
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# Documentation\n\nScenario " + run.getScenario() + " docs updated.\n",
        "documentation.md"
    );
  }

  private StageAgent.AgentResult release(OrchestrationRun run, StageInstance stage, String upstream) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("checklist", List.of(
        "Security headers present",
        "Open redirect controls verified",
        "Approvals recorded",
        "Metrics exported"
    ));
    payload.put("releaseReady", true);
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# Release Readiness\n\nAll gates satisfied for " + run.getScenario() + ".\n",
        "release-readiness.md"
    );
  }

  private String normalizeIntent(OrchestrationRun run) {
    return switch (run.getScenario()) {
      case GREENFIELD -> "Build a URL shortener with analytics and reliability controls";
      case BROWNFIELD -> "Enhance existing shortener with expiration and analytics export";
      case AMBIGUOUS -> "Improve reliability/enterprise readiness under recorded assumptions";
    };
  }

  private List<String> ambiguities(OrchestrationRun run) {
    if (run.getScenario() != ScenarioType.AMBIGUOUS) {
      return List.of();
    }
    return List.of(
        "Which reliability SLOs are in scope?",
        "Is multi-region required?",
        "What authn model is required for admin APIs?"
    );
  }

  private String normalizedProblem(OrchestrationRun run) {
    if (run.getScenario() == ScenarioType.AMBIGUOUS) {
      return "Ambiguous reliability ask normalized to: rate limiting, health checks, auditability, and human release gates. Assumptions recorded.";
    }
    return run.getRequirementText();
  }

  private List<String> impactedModules(ScenarioType scenario) {
    return switch (scenario) {
      case GREENFIELD -> List.of("urlshortener.*", "orchestration.*", "frontend");
      case BROWNFIELD ->
          List.of("UrlShortenerService", "UrlShortenerController", "ShortUrl.expiresAt", "analytics export");
      case AMBIGUOUS -> List.of("RateLimiter", "health", "PolicyGuardrails", "release gates");
    };
  }

  private List<String> apiChanges(ScenarioType scenario) {
    return switch (scenario) {
      case GREENFIELD -> List.of("+ create short URL", "+ redirect with tracking", "+ analytics");
      case BROWNFIELD -> List.of("+ expiresAt on create", "+ analytics export endpoint");
      case AMBIGUOUS -> List.of("~ harden validation", "~ expose orchestration metrics");
    };
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{\"error\":\"serialization_failed\"}";
    }
  }
}
