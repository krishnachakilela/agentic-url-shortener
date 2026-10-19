package com.schwab.agentic.orchestration.agents;

import com.schwab.agentic.orchestration.domain.OrchestrationRun;
import com.schwab.agentic.orchestration.domain.StageInstance;

@FunctionalInterface
public interface StageAgent {
  AgentResult execute(OrchestrationRun run, StageInstance stage, String upstreamContext);

  record AgentResult(boolean success, String outputJson, String artifactContent, String artifactFileName) {
    public static AgentResult ok(String outputJson, String artifactContent, String artifactFileName) {
      return new AgentResult(true, outputJson, artifactContent, artifactFileName);
    }

    public static AgentResult fail(String outputJson) {
      return new AgentResult(false, outputJson, null, null);
    }
  }
}
