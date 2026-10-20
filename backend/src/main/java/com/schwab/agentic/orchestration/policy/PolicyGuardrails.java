package com.schwab.agentic.orchestration.policy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class PolicyGuardrails {

  private static final Pattern SECRET_PATTERN = Pattern.compile(
      "(?i)(password|api[_-]?key|secret|token)\\s*[:=]\\s*\\S+"
  );
  private static final double MAX_REDIRECT_P99_MS = 10.0;

  private final ObjectMapper objectMapper;

  public PolicyGuardrails(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public PolicyResult evaluateEntry(StageInstance stage, OrchestrationRun run) {
    List<String> violations = new ArrayList<>();
    String requirementText = run.getRequirementText();
    if (requirementText != null && SECRET_PATTERN.matcher(requirementText).find()) {
      violations.add("Requirement text appears to contain secrets");
    }
    if (stage.getStageType() == StageType.RELEASE_READINESS && !stage.isRequiresApproval()) {
      violations.add("Release stage must require human approval");
    }
    return new PolicyResult(violations.isEmpty(), violations);
  }

  public PolicyResult evaluateExit(StageInstance stage, OrchestrationRun run, String outputJson) {
    List<String> violations = new ArrayList<>();
    if (outputJson == null || outputJson.isBlank()) {
      violations.add("Stage produced empty output");
      return new PolicyResult(false, violations);
    }
    if (SECRET_PATTERN.matcher(outputJson).find()) {
      violations.add("Stage output appears to contain secrets");
    }
    if (stage.getStageType() == StageType.TESTING
        && !outputJson.toLowerCase(Locale.ROOT).contains("passed")) {
      violations.add("Testing stage did not report passed validation");
    }
    if (stage.getStageType() == StageType.RELEASE_READINESS
        && run.getScenario() == ScenarioType.AMBIGUOUS) {
      violations.addAll(evaluateAmbiguousReleaseGates(outputJson));
    }
    return new PolicyResult(violations.isEmpty(), violations);
  }

  private List<String> evaluateAmbiguousReleaseGates(String outputJson) {
    List<String> violations = new ArrayList<>();
    try {
      JsonNode root = objectMapper.readTree(outputJson);
      JsonNode gates = root.path("gates");
      if (gates.isMissingNode() || !gates.isObject()) {
        violations.add("Ambiguous release requires formal gates object");
        return violations;
      }
      requireGatePassed(gates, "security", violations);
      requireGatePassed(gates, "compliance", violations);
      requireSloGate(gates.path("slo"), violations);

      JsonNode compliance = gates.path("compliance");
      if (compliance.path("criticalVulns").isNumber()
          && compliance.path("criticalVulns").asInt() > 0) {
        violations.add("Compliance gate failed: unresolved critical vulnerabilities");
      }
    } catch (Exception ex) {
      violations.add("Ambiguous release gate payload is not valid JSON");
    }
    return violations;
  }

  private void requireGatePassed(JsonNode gates, String name, List<String> violations) {
    JsonNode gate = gates.path(name);
    if (gate.isMissingNode() || !gate.path("passed").asBoolean(false)) {
      violations.add(name + " gate failed or missing");
    }
  }

  private void requireSloGate(JsonNode slo, List<String> violations) {
    if (slo.isMissingNode() || !slo.path("passed").asBoolean(false)) {
      violations.add("slo gate failed or missing");
      return;
    }
    if (slo.path("p99RedirectLatencyMs").isNumber()
        && slo.path("p99RedirectLatencyMs").asDouble() > MAX_REDIRECT_P99_MS) {
      violations.add("SLO gate failed: redirect p99 latency exceeds " + MAX_REDIRECT_P99_MS + " ms");
    }
  }

  public String redact(String value) {
    if (value == null) {
      return null;
    }
    return SECRET_PATTERN.matcher(value).replaceAll("$1=[REDACTED]");
  }

  public record PolicyResult(boolean allowed, List<String> violations) {
  }
}
