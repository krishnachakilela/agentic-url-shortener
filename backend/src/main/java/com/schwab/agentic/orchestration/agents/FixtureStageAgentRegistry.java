package com.schwab.agentic.orchestration.agents;

import tools.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.application.RedirectLatencyProbe;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageType;
import com.schwab.agentic.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic fixture agents for offline demos (no live LLM required).
 * They propose reviewable artifacts; they do not author the URL shortener domain.
 * Release SLO evidence is measured live via {@link RedirectLatencyProbe}.
 */
@Component
public class FixtureStageAgentRegistry {

  private final ObjectMapper objectMapper;
  private final AppProperties appProperties;
  private final RedirectLatencyProbe redirectLatencyProbe;

  public FixtureStageAgentRegistry(
      ObjectMapper objectMapper,
      AppProperties appProperties,
      RedirectLatencyProbe redirectLatencyProbe
  ) {
    this.objectMapper = objectMapper;
    this.appProperties = appProperties;
    this.redirectLatencyProbe = redirectLatencyProbe;
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
    payload.put("upstream", upstream);
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
    payload.put("upstream", upstream);
    payload.put("priorFailureFeedback", extractFailureFeedback(upstream));
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
    payload.put("upstream", upstream);
    payload.put("priorFailureFeedback", extractFailureFeedback(upstream));
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# UI Implementation Notes\n\nApproval console + shortener admin for scenario " + run.getScenario() + "\n",
        "implement-ui.md"
    );
  }

  private StageAgent.AgentResult testing(OrchestrationRun run, StageInstance stage, String upstream) {
    int failUntil = appProperties.orchestration().testingFailUntilAttempt();
    if (failUntil > 0 && stage.getAttempt() < failUntil) {
      Map<String, Object> failure = new LinkedHashMap<>();
      failure.put("unit", "failed");
      failure.put("integration", "failed");
      failure.put("attempt", stage.getAttempt());
      failure.put("logs", List.of(
          "AssertionError: expected redirect latency under SLO",
          "FAILURE at UrlShortenerControllerTest.test_redirect_tracks_click"
      ));
      failure.put("feedbackForDeveloper", "Fix redirect path regressions before re-entering testing sync barrier");
      return StageAgent.AgentResult.fail(toJson(failure));
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("unit", "passed");
    payload.put("integration", "passed");
    payload.put("coverageTarget", ">90% critical paths");
    payload.put("attempt", stage.getAttempt());
    payload.put("upstream", upstream);
    payload.put("risks", List.of("LLM non-determinism mitigated by fixture mode"));
    return StageAgent.AgentResult.ok(
        toJson(payload),
        "# Test Report\n\nUnit: passed\nIntegration: passed\nAttempt: " + stage.getAttempt() + "\n",
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
    RedirectLatencyProbe.ProbeResult probe = redirectLatencyProbe.measure();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("checklist", List.of(
        "Security headers present",
        "Open redirect controls verified",
        "Approvals recorded",
        "Metrics exported",
        "Live redirect latency probed against engineered domain"
    ));
    payload.put("domainPositioning", Map.of(
        "urlShortener", "Engineered independently (APIs, persistence, analytics, validation)",
        "fixtureAgents", "Propose artifacts and coordinate SDLC stages; do not author domain code"
    ));
    if (run.getScenario() == ScenarioType.AMBIGUOUS) {
      payload.put("gates", ambiguousReleaseGates(probe));
    } else {
      payload.put("liveSloEvidence", sloEvidence(probe));
    }
    payload.put("releaseReady", probe.passed());
    payload.put("upstream", upstream);

    String artifact = "# Release Readiness\n\n"
        + "Scenario: " + run.getScenario() + "\n\n"
        + "## Positioning\n"
        + "- URL shortener domain is engineered independently.\n"
        + "- Fixture agents orchestrate and propose artifacts; they did not build the service.\n\n"
        + "## Live SLO probe\n"
        + "- evidenceSource: " + probe.evidenceSource() + "\n"
        + "- p99RedirectLatencyMs: " + probe.p99RedirectLatencyMs() + "\n"
        + "- samples: " + probe.samples() + "\n"
        + "- passed: " + probe.passed() + "\n"
        + "- detail: " + probe.detail() + "\n";

    if (!probe.passed()) {
      return StageAgent.AgentResult.fail(toJson(payload));
    }
    return StageAgent.AgentResult.ok(toJson(payload), artifact, "release-readiness.md");
  }

  private Map<String, Object> ambiguousReleaseGates(RedirectLatencyProbe.ProbeResult probe) {
    Map<String, Object> security = new LinkedHashMap<>();
    security.put("passed", true);
    security.put("checks", List.of("open-redirect-allowlist", "create-and-redirect-rate-limit"));

    Map<String, Object> compliance = new LinkedHashMap<>();
    compliance.put("passed", true);
    compliance.put("criticalVulns", 0);
    compliance.put("scanner", "fixture-dependency-posture");
    compliance.put("note", "Dependency posture remains declarative in fixture mode");

    Map<String, Object> gates = new LinkedHashMap<>();
    gates.put("security", security);
    gates.put("compliance", compliance);
    gates.put("slo", sloEvidence(probe));
    return gates;
  }

  private Map<String, Object> sloEvidence(RedirectLatencyProbe.ProbeResult probe) {
    Map<String, Object> slo = new LinkedHashMap<>();
    slo.put("passed", probe.passed());
    slo.put("p99RedirectLatencyMs", probe.p99RedirectLatencyMs());
    slo.put("samples", probe.samples());
    slo.put("shortCode", probe.shortCode());
    slo.put("measured", true);
    slo.put("evidenceSource", probe.evidenceSource());
    slo.put("detail", probe.detail());
    slo.put("targetAvailability", "99.9%");
    return slo;
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

  private Object extractFailureFeedback(String upstream) {
    if (upstream == null || upstream.isBlank()) {
      return null;
    }
    try {
      Map<?, ?> context = objectMapper.readValue(upstream, Map.class);
      return context.get("lastFailure");
    } catch (Exception ex) {
      return null;
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{\"error\":\"serialization_failed\"}";
    }
  }
}
