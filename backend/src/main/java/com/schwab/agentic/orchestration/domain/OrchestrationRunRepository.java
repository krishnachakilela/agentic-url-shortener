package com.schwab.agentic.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRun, String> {
  List<OrchestrationRun> findAllByOrderByCreatedAtDesc();
}
