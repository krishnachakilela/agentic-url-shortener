package com.schwab.agentic.orchestration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage_instances")
public class StageInstance {

  @Id
  @Column(length = 36, nullable = false)
  private String id;

  @Column(name = "run_id", length = 36, nullable = false)
  private String runId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private StageType stageType;

  @Column(nullable = false, length = 64)
  private String stageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private StageStatus status;

  @Column(nullable = false)
  private int sequenceOrder;

  @Column(nullable = false)
  private boolean requiresApproval;

  @Column(nullable = false)
  private boolean parallelGroup;

  @Column(length = 512)
  private String dependsOn;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String inputJson;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String outputJson;

  @Column(length = 1024)
  private String artifactPath;

  @Column(nullable = false)
  private int attempt;

  private Instant startedAt;
  private Instant completedAt;

  @Version
  private long version;

  protected StageInstance() {
  }

  public StageInstance(
      String runId,
      StageType stageType,
      String stageKey,
      int sequenceOrder,
      boolean requiresApproval,
      boolean parallelGroup,
      String dependsOn
  ) {
    this.id = UUID.randomUUID().toString();
    this.runId = runId;
    this.stageType = stageType;
    this.stageKey = stageKey;
    this.status = StageStatus.PENDING;
    this.sequenceOrder = sequenceOrder;
    this.requiresApproval = requiresApproval;
    this.parallelGroup = parallelGroup;
    this.dependsOn = dependsOn;
    this.attempt = 0;
  }

  public void markReady() {
    this.status = StageStatus.READY;
  }

  public void markRunning() {
    this.status = StageStatus.RUNNING;
    this.startedAt = Instant.now();
    this.attempt++;
  }

  public void awaitApproval() {
    this.status = StageStatus.AWAITING_APPROVAL;
  }

  public void succeed(String outputJson, String artifactPath) {
    this.status = StageStatus.SUCCEEDED;
    this.outputJson = outputJson;
    this.artifactPath = artifactPath;
    this.completedAt = Instant.now();
  }

  public void proposeForApproval(String outputJson, String artifactPath) {
    this.outputJson = outputJson;
    this.artifactPath = artifactPath;
    this.status = StageStatus.AWAITING_APPROVAL;
  }

  public void fail(String outputJson) {
    this.status = StageStatus.FAILED;
    this.outputJson = outputJson;
    this.completedAt = Instant.now();
  }

  public void rollback() {
    this.status = StageStatus.ROLLED_BACK;
    this.completedAt = Instant.now();
  }

  public void skip() {
    this.status = StageStatus.SKIPPED;
    this.completedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public StageType getStageType() {
    return stageType;
  }

  public String getStageKey() {
    return stageKey;
  }

  public StageStatus getStatus() {
    return status;
  }

  public int getSequenceOrder() {
    return sequenceOrder;
  }

  public boolean isRequiresApproval() {
    return requiresApproval;
  }

  public boolean isParallelGroup() {
    return parallelGroup;
  }

  public String getDependsOn() {
    return dependsOn;
  }

  public String getInputJson() {
    return inputJson;
  }

  public void setInputJson(String inputJson) {
    this.inputJson = inputJson;
  }

  public String getOutputJson() {
    return outputJson;
  }

  public String getArtifactPath() {
    return artifactPath;
  }

  public int getAttempt() {
    return attempt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
