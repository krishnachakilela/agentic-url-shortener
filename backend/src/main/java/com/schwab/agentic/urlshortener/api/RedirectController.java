package com.schwab.agentic.urlshortener.api;

import com.schwab.agentic.urlshortener.application.RateLimiter;
import com.schwab.agentic.urlshortener.application.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.regex.Pattern;

@RestController
public class RedirectController {

  private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9]{4,16}$");

  private final UrlShortenerService urlShortenerService;
  private final RateLimiter rateLimiter;

  public RedirectController(UrlShortenerService urlShortenerService, RateLimiter rateLimiter) {
    this.urlShortenerService = urlShortenerService;
    this.rateLimiter = rateLimiter;
  }

  @GetMapping("/r/{code}")
  public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
    if (!CODE_PATTERN.matcher(code).matches()) {
      return ResponseEntity.badRequest().build();
    }
    if (!rateLimiter.allow(clientKey(request))) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
    }
    String target = urlShortenerService.resolveAndTrack(
        code,
        request.getHeader(HttpHeaders.USER_AGENT),
        request.getHeader(HttpHeaders.REFERER)
    );
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(target))
        .build();
  }

  private String clientKey(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
  }
}
