package com.schwab.agentic.orchestration.domain;

public enum RunStatus {
  PENDING,
  RUNNING,
  AWAITING_APPROVAL,
  REPLANNING,
  SUCCEEDED,
  FAILED,
  ROLLED_BACK,
  SAFE_STOPPED
}
