package com.schwab.agentic.orchestration.domain;

public enum StageStatus {
  PENDING,
  READY,
  RUNNING,
  AWAITING_APPROVAL,
  SUCCEEDED,
  FAILED,
  SKIPPED,
  ROLLED_BACK
}
