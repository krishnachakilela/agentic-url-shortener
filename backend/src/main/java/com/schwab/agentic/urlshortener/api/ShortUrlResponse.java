package com.schwab.agentic.urlshortener.api;

import java.time.Instant;

public record ShortUrlResponse(
    String shortCode,
    String shortUrl,
    String originalUrl,
    Instant createdAt,
    Instant expiresAt,
    long clickCount
) {
}
