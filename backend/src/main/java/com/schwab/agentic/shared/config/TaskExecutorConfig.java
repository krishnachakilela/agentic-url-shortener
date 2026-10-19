package com.schwab.agentic.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class TaskExecutorConfig {

  @Bean(name = "applicationTaskExecutor")
  public TaskExecutor applicationTaskExecutor() {
    return new SimpleAsyncTaskExecutor("orch-");
  }
}
