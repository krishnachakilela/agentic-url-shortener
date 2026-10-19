package com.schwab.agentic.urlshortener.application;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlValidator {

  private static final Set<String> BLOCKED_SCHEMES = Set.of("javascript", "data", "file", "ftp");
  private static final Set<String> BLOCKED_HOSTS = Set.of(
      "localhost", "127.0.0.1", "0.0.0.0", "::1", "metadata.google.internal"
  );

  public void validateTargetUrl(String rawUrl, int maxLength) {
    if (rawUrl == null || rawUrl.isBlank()) {
      throw new IllegalArgumentException("URL is required");
    }
    if (rawUrl.length() > maxLength) {
      throw new IllegalArgumentException("URL exceeds max length");
    }

    URI uri;
    try {
      uri = URI.create(rawUrl.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid URL");
    }

    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      throw new IllegalArgumentException("Only http/https URLs are allowed");
    }
    if (BLOCKED_SCHEMES.contains(scheme)) {
      throw new IllegalArgumentException("Blocked URL scheme");
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("URL host is required");
    }
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (BLOCKED_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".local")
        || normalizedHost.startsWith("10.")
        || normalizedHost.startsWith("192.168.")
        || isLinkLocal(normalizedHost)) {
      throw new IllegalArgumentException("Target host is not allowed");
    }
  }

  private boolean isLinkLocal(String host) {
    if (!host.startsWith("169.254.")) {
      return false;
    }
    return true;
  }
}
