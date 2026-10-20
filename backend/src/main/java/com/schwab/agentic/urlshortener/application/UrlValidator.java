package com.schwab.agentic.urlshortener.application;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
    if (BLOCKED_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".local")) {
      throw new IllegalArgumentException("Target host is not allowed");
    }
    if (isPrivateOrLinkLocalHost(normalizedHost)) {
      throw new IllegalArgumentException("Target host is not allowed");
    }
  }

  private boolean isPrivateOrLinkLocalHost(String host) {
    if (host.startsWith("10.")
        || host.startsWith("192.168.")
        || host.startsWith("169.254.")
        || isRfc1918ClassB(host)) {
      return true;
    }
    if (!looksLikeIpLiteral(host)) {
      return false;
    }
    try {
      InetAddress address = InetAddress.getByName(host);
      return address.isAnyLocalAddress()
          || address.isLoopbackAddress()
          || address.isLinkLocalAddress()
          || address.isSiteLocalAddress();
    } catch (UnknownHostException ex) {
      return false;
    }
  }

  private boolean looksLikeIpLiteral(String host) {
    if (host.contains(":")) {
      return true;
    }
    String[] parts = host.split("\\.");
    if (parts.length != 4) {
      return false;
    }
    for (String part : parts) {
      try {
        int value = Integer.parseInt(part);
        if (value < 0 || value > 255) {
          return false;
        }
      } catch (NumberFormatException ex) {
        return false;
      }
    }
    return true;
  }

  private boolean isRfc1918ClassB(String host) {
    if (!host.startsWith("172.")) {
      return false;
    }
    String[] parts = host.split("\\.");
    if (parts.length < 2) {
      return false;
    }
    try {
      int secondOctet = Integer.parseInt(parts[1]);
      return secondOctet >= 16 && secondOctet <= 31;
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}
