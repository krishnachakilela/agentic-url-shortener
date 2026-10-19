package com.schwab.agentic.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SecurityHeadersInterceptor implements HandlerInterceptor {

  private static final String H2_CONSOLE_PATH_PREFIX = "/h2-console";

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String path = request.getRequestURI();
    if (path != null && path.startsWith(H2_CONSOLE_PATH_PREFIX)) {
      // H2 console uses frames and inline scripts; keep headers scoped for local debugging only.
      response.setHeader("X-Content-Type-Options", "nosniff");
      response.setHeader("X-Frame-Options", "SAMEORIGIN");
      response.setHeader("Content-Security-Policy",
          "default-src 'self'; frame-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
      response.setHeader("Referrer-Policy", "no-referrer");
      return true;
    }

    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    response.setHeader("Content-Security-Policy", "default-src 'self'");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    return true;
  }
}
