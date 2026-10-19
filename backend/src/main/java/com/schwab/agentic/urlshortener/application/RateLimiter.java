package com.schwab.agentic.urlshortener.application;

import com.schwab.agentic.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final AppProperties appProperties;
  private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
  private final Map<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

  public RateLimiter(AppProperties appProperties, ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    this.appProperties = appProperties;
    this.redisTemplateProvider = redisTemplateProvider;
  }

  public boolean allow(String clientKey) {
    int limit = appProperties.shortener().rateLimitPerMinute();
    StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
    if (redisTemplate == null) {
      return allowLocal(clientKey, limit);
    }
    try {
      String redisKey = "rl:" + clientKey;
      Long count = redisTemplate.opsForValue().increment(redisKey);
      if (count != null && count == 1L) {
        redisTemplate.expire(redisKey, WINDOW);
      }
      return count == null || count <= limit;
    } catch (Exception ex) {
      log.warn("Redis rate limit unavailable, using local fallback");
      return allowLocal(clientKey, limit);
    }
  }

  private boolean allowLocal(String clientKey, int limit) {
    long bucket = System.currentTimeMillis() / WINDOW.toMillis();
    WindowCounter counter = localCounters.compute(clientKey, (key, existing) -> {
      if (existing == null || existing.bucket != bucket) {
        return new WindowCounter(bucket, new AtomicInteger(0));
      }
      return existing;
    });
    return counter.count.incrementAndGet() <= limit;
  }

  private record WindowCounter(long bucket, AtomicInteger count) {
  }
}
