package com.schwab.agentic.urlshortener.api;

import com.schwab.agentic.shared.config.AppProperties;
import com.schwab.agentic.urlshortener.application.RateLimiter;
import com.schwab.agentic.urlshortener.application.UrlShortenerService;
import com.schwab.agentic.urlshortener.domain.ShortUrl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class UrlShortenerController {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,16}$");

  private final UrlShortenerService urlShortenerService;
  private final RateLimiter rateLimiter;
  private final AppProperties appProperties;

  public UrlShortenerController(
      UrlShortenerService urlShortenerService,
      RateLimiter rateLimiter,
      AppProperties appProperties
  ) {
    this.urlShortenerService = urlShortenerService;
    this.rateLimiter = rateLimiter;
    this.appProperties = appProperties;
  }

  @PostMapping("/urls")
  public ResponseEntity<ShortUrlResponse> create(
      @Valid @RequestBody CreateShortUrlRequest request,
      HttpServletRequest servletRequest
  ) {
    if (!rateLimiter.allow(clientKey(servletRequest))) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
    ShortUrl created = urlShortenerService.create(request.url(), request.expiresAt());
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
  }

  @GetMapping("/urls/{code}")
  public ShortUrlResponse get(@PathVariable String code) {
    requireValidCode(code);
    return toResponse(urlShortenerService.getByCode(code));
  }

  @GetMapping("/urls/{code}/analytics")
  public UrlShortenerService.AnalyticsView analytics(@PathVariable String code) {
    requireValidCode(code);
    return urlShortenerService.analytics(code);
  }

  @GetMapping("/urls/{code}/analytics/export")
  public List<UrlShortenerService.AnalyticsExportRow> export(@PathVariable String code) {
    requireValidCode(code);
    return urlShortenerService.exportAnalytics(code);
  }

  private ShortUrlResponse toResponse(ShortUrl shortUrl) {
    String shortLink = appProperties.shortener().baseUrl().replaceAll("/$", "") + "/r/" + shortUrl.getShortCode();
    return new ShortUrlResponse(
        shortUrl.getShortCode(),
        shortLink,
        shortUrl.getOriginalUrl(),
        shortUrl.getCreatedAt(),
        shortUrl.getExpiresAt(),
        shortUrl.getClickCount()
    );
  }

  private void requireValidCode(String code) {
    if (code == null || !CODE_PATTERN.matcher(code).matches()) {
      throw new IllegalArgumentException("Invalid short code");
    }
  }

  private String clientKey(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
  }
}
