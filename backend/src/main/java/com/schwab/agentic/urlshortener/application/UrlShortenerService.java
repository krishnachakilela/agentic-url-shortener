package com.schwab.agentic.urlshortener.application;

import com.schwab.agentic.shared.config.AppProperties;
import com.schwab.agentic.shared.web.NotFoundException;
import com.schwab.agentic.urlshortener.domain.ClickEvent;
import com.schwab.agentic.urlshortener.domain.ClickEventRepository;
import com.schwab.agentic.urlshortener.domain.ShortUrl;
import com.schwab.agentic.urlshortener.domain.ShortUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UrlShortenerService {

  private static final int MAX_CODE_ATTEMPTS = 5;

  private final ShortUrlRepository shortUrlRepository;
  private final ClickEventRepository clickEventRepository;
  private final UrlValidator urlValidator;
  private final CodeGenerator codeGenerator;
  private final AppProperties appProperties;

  public UrlShortenerService(
      ShortUrlRepository shortUrlRepository,
      ClickEventRepository clickEventRepository,
      UrlValidator urlValidator,
      CodeGenerator codeGenerator,
      AppProperties appProperties
  ) {
    this.shortUrlRepository = shortUrlRepository;
    this.clickEventRepository = clickEventRepository;
    this.urlValidator = urlValidator;
    this.codeGenerator = codeGenerator;
    this.appProperties = appProperties;
  }

  @Transactional
  public ShortUrl create(String originalUrl, Instant expiresAt) {
    urlValidator.validateTargetUrl(originalUrl, appProperties.shortener().maxUrlLength());
    if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
      throw new IllegalArgumentException("Expiration must be in the future");
    }

    String code = allocateCode();
    ShortUrl shortUrl = new ShortUrl(code, originalUrl.trim(), expiresAt);
    return shortUrlRepository.save(shortUrl);
  }

  @Transactional(readOnly = true)
  public ShortUrl getByCode(String code) {
    return shortUrlRepository.findByShortCode(code)
        .orElseThrow(() -> new NotFoundException("Short URL not found"));
  }

  @Transactional
  public String resolveAndTrack(String code, String userAgent, String referrer) {
    ShortUrl shortUrl = getByCode(code);
    Instant now = Instant.now();
    if (!shortUrl.isActive() || shortUrl.isExpired(now)) {
      throw new NotFoundException("Short URL unavailable");
    }
    shortUrl.recordClick();
    clickEventRepository.save(new ClickEvent(code, userAgent, referrer));
    return shortUrl.getOriginalUrl();
  }

  @Transactional(readOnly = true)
  public AnalyticsView analytics(String code) {
    ShortUrl shortUrl = getByCode(code);
    List<ClickEvent> events = clickEventRepository.findByShortCodeOrderByOccurredAtDesc(code);
    long lastDay = clickEventRepository.countSince(code, Instant.now().minusSeconds(86400));
    return new AnalyticsView(
        shortUrl.getShortCode(),
        shortUrl.getOriginalUrl(),
        shortUrl.getClickCount(),
        lastDay,
        shortUrl.getCreatedAt(),
        shortUrl.getExpiresAt(),
        events.stream().limit(50).map(e -> new ClickView(
            e.getOccurredAt(),
            e.getUserAgent(),
            e.getReferrer()
        )).toList()
    );
  }

  @Transactional(readOnly = true)
  public List<AnalyticsExportRow> exportAnalytics(String code) {
    ShortUrl shortUrl = getByCode(code);
    return clickEventRepository.findByShortCodeOrderByOccurredAtDesc(code).stream()
        .map(e -> new AnalyticsExportRow(
            shortUrl.getShortCode(),
            shortUrl.getOriginalUrl(),
            e.getOccurredAt(),
            e.getUserAgent(),
            e.getReferrer()
        ))
        .toList();
  }

  private String allocateCode() {
    int length = appProperties.shortener().codeLength();
    for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
      String code = codeGenerator.generate(length);
      if (!shortUrlRepository.existsByShortCode(code)) {
        return code;
      }
    }
    throw new IllegalStateException("Unable to allocate short code");
  }

  public record AnalyticsView(
      String shortCode,
      String originalUrl,
      long totalClicks,
      long clicksLast24h,
      Instant createdAt,
      Instant expiresAt,
      List<ClickView> recentClicks
  ) {
  }

  public record ClickView(Instant occurredAt, String userAgent, String referrer) {
  }

  public record AnalyticsExportRow(
      String shortCode,
      String originalUrl,
      Instant occurredAt,
      String userAgent,
      String referrer
  ) {
  }
}
