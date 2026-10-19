package com.schwab.agentic.orchestration.application;

import com.schwab.agentic.orchestration.domain.ScenarioType;
import com.schwab.agentic.orchestration.domain.StageInstance;
import com.schwab.agentic.orchestration.domain.StageType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an explicit dependency graph per scenario.
 * Edges are encoded in dependsOn (comma-separated stage keys).
 */
@Component
public class DependencyGraphFactory {

  public List<StageInstance> buildGraph(String runId, ScenarioType scenario) {
    List<StageInstance> stages = new ArrayList<>();

    stages.add(stage(runId, StageType.REQUIREMENTS, "requirements", 10, false, false, ""));
    stages.add(stage(runId, StageType.ARCHITECTURE, "architecture", 20, false, false, "requirements"));

    if (scenario == ScenarioType.BROWNFIELD) {
      stages.add(stage(runId, StageType.IMPLEMENT_API, "implement-api", 30, true, true, "architecture"));
      stages.add(stage(runId, StageType.IMPLEMENT_UI, "implement-ui", 30, false, true, "architecture"));
      stages.add(stage(runId, StageType.TESTING, "testing", 40, false, false, "implement-api,implement-ui"));
      stages.add(stage(runId, StageType.DOCUMENTATION, "documentation", 50, false, false, "testing"));
      stages.add(stage(runId, StageType.RELEASE_READINESS, "release", 60, true, false, "documentation"));
      return stages;
    }

    if (scenario == ScenarioType.AMBIGUOUS) {
      stages.add(stage(runId, StageType.IMPLEMENT_API, "implement-api", 30, true, false, "architecture"));
      stages.add(stage(runId, StageType.TESTING, "testing", 40, false, false, "implement-api"));
      stages.add(stage(runId, StageType.DOCUMENTATION, "documentation", 50, false, false, "testing"));
      stages.add(stage(runId, StageType.RELEASE_READINESS, "release", 60, true, false, "documentation"));
      return stages;
    }

    // GREENFIELD: design -> parallel implement API/UI -> sync test -> docs -> release gate
    stages.add(stage(runId, StageType.IMPLEMENT_API, "implement-api", 30, false, true, "architecture"));
    stages.add(stage(runId, StageType.IMPLEMENT_UI, "implement-ui", 30, false, true, "architecture"));
    stages.add(stage(runId, StageType.TESTING, "testing", 40, false, false, "implement-api,implement-ui"));
    stages.add(stage(runId, StageType.DOCUMENTATION, "documentation", 50, false, false, "testing"));
    stages.add(stage(runId, StageType.RELEASE_READINESS, "release", 60, true, false, "documentation"));
    return stages;
  }

  private StageInstance stage(
      String runId,
      StageType type,
      String key,
      int order,
      boolean requiresApproval,
      boolean parallel,
      String dependsOn
  ) {
    return new StageInstance(runId, type, key, order, requiresApproval, parallel, dependsOn);
  }
}
