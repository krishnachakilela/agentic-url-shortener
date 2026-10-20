package com.schwab.agentic.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

  private final AppProperties appProperties;
  private final SecurityHeadersInterceptor securityHeadersInterceptor;
  private final LocalAdminInterceptor localAdminInterceptor;

  public WebSecurityConfig(
      AppProperties appProperties,
      SecurityHeadersInterceptor securityHeadersInterceptor,
      LocalAdminInterceptor localAdminInterceptor
  ) {
    this.appProperties = appProperties;
    this.securityHeadersInterceptor = securityHeadersInterceptor;
    this.localAdminInterceptor = localAdminInterceptor;
  }

  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOrigins(appProperties.cors().allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return new CorsFilter(source);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(securityHeadersInterceptor);
    registry.addInterceptor(localAdminInterceptor).addPathPatterns("/api/v1/orchestration/**");
  }
}
