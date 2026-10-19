package com.schwab.agentic.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
@Import(DataRedisAutoConfiguration.class)
public class RedisConfig {
}
