package com.schwab.agentic.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "click_events")
public class ClickEvent {

  @Id
  @Column(length = 36, nullable = false)
  private String id;

  @Column(name = "short_code", length = 16, nullable = false)
  private String shortCode;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "referrer", length = 512)
  private String referrer;

  protected ClickEvent() {
  }

  public ClickEvent(String shortCode, String userAgent, String referrer) {
    this.id = UUID.randomUUID().toString();
    this.shortCode = shortCode;
    this.occurredAt = Instant.now();
    this.userAgent = truncate(userAgent, 512);
    this.referrer = truncate(referrer, 512);
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  public String getId() {
    return id;
  }

  public String getShortCode() {
    return shortCode;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public String getReferrer() {
    return referrer;
  }
}
