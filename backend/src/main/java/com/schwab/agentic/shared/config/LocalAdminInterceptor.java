package com.schwab.agentic.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;

/**
 * Optional guard for orchestration mutating endpoints when require-local-admin is enabled.
 */
@Component
public class LocalAdminInterceptor implements HandlerInterceptor {

  private final AppProperties appProperties;

  public LocalAdminInterceptor(AppProperties appProperties) {
    this.appProperties = appProperties;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (!appProperties.orchestration().requireLocalAdmin()) {
      return true;
    }
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String path = request.getRequestURI();
    if (path == null || !path.startsWith("/api/v1/orchestration/")) {
      return true;
    }
    if (isLoopback(request.getRemoteAddr())) {
      return true;
    }
    response.sendError(HttpStatus.FORBIDDEN.value(), "Request failed");
    return false;
  }

  private boolean isLoopback(String remoteAddr) {
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return false;
    }
    try {
      return InetAddress.getByName(remoteAddr).isLoopbackAddress();
    } catch (Exception ex) {
      return false;
    }
  }
}
