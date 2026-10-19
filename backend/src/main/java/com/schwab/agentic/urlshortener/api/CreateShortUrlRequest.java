package com.schwab.agentic.urlshortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateShortUrlRequest(
    @NotBlank
    @Size(min = 8, max = 2048)
    String url,

    Instant expiresAt
) {
}
