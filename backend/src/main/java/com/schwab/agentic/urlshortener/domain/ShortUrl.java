package com.schwab.agentic.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

  @Id
  @Column(length = 36, nullable = false)
  private String id;

  @Column(name = "short_code", length = 16, nullable = false, unique = true)
  private String shortCode;

  @Column(name = "original_url", length = 2048, nullable = false)
  private String originalUrl;

  @Column(name = "click_count", nullable = false)
  private long clickCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "active", nullable = false)
  private boolean active;

  @Version
  private long version;

  protected ShortUrl() {
  }

  public ShortUrl(String shortCode, String originalUrl, Instant expiresAt) {
    this.id = UUID.randomUUID().toString();
    this.shortCode = shortCode;
    this.originalUrl = originalUrl;
    this.clickCount = 0L;
    this.createdAt = Instant.now();
    this.expiresAt = expiresAt;
    this.active = true;
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && now.isAfter(expiresAt);
  }

  public void recordClick() {
    this.clickCount++;
  }

  public void deactivate() {
    this.active = false;
  }

  public String getId() {
    return id;
  }

  public String getShortCode() {
    return shortCode;
  }

  public String getOriginalUrl() {
    return originalUrl;
  }

  public long getClickCount() {
    return clickCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isActive() {
    return active;
  }
}
