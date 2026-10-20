package com.schwab.agentic.orchestration.application;

import com.schwab.agentic.urlshortener.application.UrlShortenerService;
import com.schwab.agentic.urlshortener.domain.ShortUrl;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Live release-gate evidence: measures in-process redirect resolve latency
 * against the engineered URL shortener domain (not fixture-asserted).
 */
@Component
public class RedirectLatencyProbe {

  public static final String EVIDENCE_SOURCE = "live-redirect-probe";
  public static final String PROBE_TARGET_URL = "https://example.com/agentic-slo-probe";
  private static final int SAMPLE_COUNT = 5;
  private static final double MAX_P99_MS = 10.0;

  private final UrlShortenerService urlShortenerService;

  public RedirectLatencyProbe(UrlShortenerService urlShortenerService) {
    this.urlShortenerService = urlShortenerService;
  }

  public ProbeResult measure() {
    ShortUrl shortUrl = urlShortenerService.create(PROBE_TARGET_URL, null);
    double[] samplesMs = new double[SAMPLE_COUNT];

    for (int i = 0; i < SAMPLE_COUNT; i++) {
      long startedNanos = System.nanoTime();
      String resolved = urlShortenerService.resolveAndTrack(
          shortUrl.getShortCode(),
          "agentic-slo-probe",
          "orchestration-release-gate"
      );
      long elapsedNanos = System.nanoTime() - startedNanos;
      if (!PROBE_TARGET_URL.equals(resolved)) {
        return new ProbeResult(
            false,
            Double.POSITIVE_INFINITY,
            i + 1,
            shortUrl.getShortCode(),
            EVIDENCE_SOURCE,
            "Resolved URL mismatch"
        );
      }
      samplesMs[i] = elapsedNanos / 1_000_000.0;
    }

    double p99Ms = approximateP99(samplesMs);
    boolean passed = p99Ms <= MAX_P99_MS;
    return new ProbeResult(
        passed,
        p99Ms,
        SAMPLE_COUNT,
        shortUrl.getShortCode(),
        EVIDENCE_SOURCE,
        passed ? "Redirect path within SLO budget" : "Redirect p99 exceeded " + MAX_P99_MS + " ms"
    );
  }

  private double approximateP99(double[] samplesMs) {
    double[] sorted = Arrays.copyOf(samplesMs, samplesMs.length);
    Arrays.sort(sorted);
    int index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.99) - 1);
    if (index < 0) {
      return sorted[sorted.length - 1];
    }
    return sorted[index];
  }

  public record ProbeResult(
      boolean passed,
      double p99RedirectLatencyMs,
      int samples,
      String shortCode,
      String evidenceSource,
      String detail
  ) {
  }
}
