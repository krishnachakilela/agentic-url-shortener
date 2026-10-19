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
@Table(name = "audit_events")
public class AuditEvent {

  @Id
  @Column(length = 36, nullable = false)
  private String id;

  @Column(name = "run_id", length = 36, nullable = false)
  private String runId;

  @Column(name = "stage_key", length = 64)
  private String stageKey;

  @Column(nullable = false, length = 64)
  private String eventType;

  @Column(nullable = false, length = 64)
  private String actor;

  @JdbcTypeCode(SqlTypes.LONGVARCHAR)
  private String detailJson;

  @Column(nullable = false)
  private Instant occurredAt;

  protected AuditEvent() {
  }

  public AuditEvent(String runId, String stageKey, String eventType, String actor, String detailJson) {
    this.id = UUID.randomUUID().toString();
    this.runId = runId;
    this.stageKey = stageKey;
    this.eventType = eventType;
    this.actor = actor;
    this.detailJson = detailJson;
    this.occurredAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getRunId() {
    return runId;
  }

  public String getStageKey() {
    return stageKey;
  }

  public String getEventType() {
    return eventType;
  }

  public String getActor() {
    return actor;
  }

  public String getDetailJson() {
    return detailJson;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
    