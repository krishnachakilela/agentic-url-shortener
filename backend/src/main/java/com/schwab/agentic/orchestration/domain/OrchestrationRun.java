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
@Table(name = "orchestration_runs")
public class OrchestrationRun {

  @Id
  @Column(length = 36, nullable = false)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ScenarioType scenario;

  @Column(nullable = false, length = 512)
  private String title;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  @Column(nullable = false)
  private String requirementText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RunStatus status;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String contextJson;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String assumptionsJson;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String summaryMarkdown;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant startedAt;
  private Instant completedAt;

  @Column(nullable = false)
  private int retryCount;

  @Column(nullable = false)
  private int rollbackCount;

  @Version
  private long version;

  protected OrchestrationRun() {
  }

  public OrchestrationRun(ScenarioType scenario, String title, String requirementText) {
    this.id = UUID.randomUUID().toString();
    this.scenario = scenario;
    this.title = title;
    this.requirementText = requirementText;
    this.status = RunStatus.PENDING;
    this.createdAt = Instant.now();
    this.retryCount = 0;
    this.rollbackCount = 0;
    this.contextJson = "{}";
    this.assumptionsJson = "[]";
  }

  public void markRunning() {
    this.status = RunStatus.RUNNING;
    if (this.startedAt == null) {
      this.startedAt = Instant.now();
    }
  }

  public void awaitApproval() {
    this.status = RunStatus.AWAITING_APPROVAL;
  }

  public void markSucceeded(String summary) {
    this.status = RunStatus.SUCCEEDED;
    this.summaryMarkdown = summary;
    this.completedAt = Instant.now();
  }

  public void markFailed() {
    this.status = RunStatus.FAILED;
    this.completedAt = Instant.now();
  }

  public void markRolledBack() {
    this.status = RunStatus.ROLLED_BACK;
    this.rollbackCount++;
    this.completedAt = Instant.now();
  }

  public void safeStop() {
    this.status = RunStatus.SAFE_STOPPED;
    this.completedAt = Instant.now();
  }

  public void replan() {
    this.status = RunStatus.REPLANNING;
  }

  public void incrementRetry() {
    this.retryCount++;
  }

  public void updateContext(String contextJson) {
    this.contextJson = contextJson;
  }

  public void updateAssumptions(String assumptionsJson) {
    this.assumptionsJson = assumptionsJson;
  }

  public String getId() {
    return id;
  }

  public ScenarioType getScenario() {
    return scenario;
  }

  public String getTitle() {
    return title;
  }

  public String getRequirementText() {
    return requirementText;
  }

  public RunStatus getStatus() {
    return status;
  }

  public String getContextJson() {
    return contextJson;
  }

  public String getAssumptionsJson() {
    return assumptionsJson;
  }

  public String getSummaryMarkdown() {
    return summaryMarkdown;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public int getRollbackCount() {
    return rollbackCount;
  }
}
