package com.schwab.agentic.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gate_decisions")
public class GateDecision {

  @Id
  @Column(length = 36, nullable = false)
  private String id;

  @Column(name = "run_id", length = 36, nullable = false)
  private String runId;

  @Column(name = "stage_id", length = 36, nullable = false)
  private String stageId;

  @Column(nullable = false)
  private boolean approved;

  @Column(nullable = false, length = 128)
  private String decidedBy;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String reason;

  @Column(nullable = false)
  private Instant decidedAt;

  protected GateDecision() {
  }

  public GateDecision(String runId, String stageId, boolean approved, String decidedBy, String reason) {
    this.id = UUID.randomUUID().toString();
    this.runId = runId;
    this.stageId = stageId;
    this.approved = approved;
    this.decidedBy = decidedBy;
    this.reason = reason;
    this.decidedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public String getStageId() {
    return stageId;
  }

  public boolean isApproved() {
    return approved;
  }

  public String getDecidedBy() {
    return decidedBy;
  }

  public String getReason() {
    return reason;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
