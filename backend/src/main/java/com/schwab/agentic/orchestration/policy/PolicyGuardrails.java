package com.schwab.agentic.orchestration.policy;

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

  public PolicyResult evaluateEntry(StageInstance stage, String requirementText) {
    List<String> violations = new ArrayList<>();
    if (requirementText != null && SECRET_PATTERN.matcher(requirementText).find()) {
      violations.add("Requirement text appears to contain secrets");
    }
    if (stage.getStageType() == StageType.RELEASE_READINESS && !stage.isRequiresApproval()) {
      violations.add("Release stage must require human approval");
    }
    return new PolicyResult(violations.isEmpty(), violations);
  }

  public PolicyResult evaluateExit(StageInstance stage, String outputJson) {
    List<String> violations = new ArrayList<>();
    if (outputJson == null || outputJson.isBlank()) {
      violations.add("Stage produced empty output");
    } else if (SECRET_PATTERN.matcher(outputJson).find()) {
      violations.add("Stage output appears to contain secrets");
    }
    if (stage.getStageType() == StageType.TESTING
        && outputJson != null
        && !outputJson.toLowerCase(Locale.ROOT).contains("passed")) {
      violations.add("Testing stage did not report passed validation");
    }
    return new PolicyResult(violations.isEmpty(), violations);
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
