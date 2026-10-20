package com.schwab.agentic.urlshortener.api;

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

  public RedirectController(UrlShortenerService urlShortenerService) {
    this.urlShortenerService = urlShortenerService;
  }

  @GetMapping("/r/{code}")
  public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
    if (!CODE_PATTERN.matcher(code).matches()) {
      return ResponseEntity.badRequest().build();
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
}
