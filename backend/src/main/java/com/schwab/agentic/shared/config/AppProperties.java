package com.schwab.agentic.shared.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    Cors cors,
    Shortener shortener,
    Orchestration orchestration
) {
  public record Cors(@NotEmpty List<String> allowedOrigins) {
  }

  public record Shortener(
      @NotBlank String baseUrl,
      @Min(4) @Max(16) int codeLength,
      @Min(32) @Max(4096) int maxUrlLength,
      @Min(1) @Max(10000) int rateLimitPerMinute
  ) {
  }

  public record Orchestration(
      @NotBlank String agentMode,
      @Min(0) @Max(10) int maxRetries,
      @Min(10) @Max(3600) int stageTimeoutSeconds,
      @NotBlank String artifactsDir
  ) {
  }
}
