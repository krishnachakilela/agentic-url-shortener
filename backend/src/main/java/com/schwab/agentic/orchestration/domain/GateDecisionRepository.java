package com.schwab.agentic.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateDecisionRepository extends JpaRepository<GateDecision, String> {
  List<GateDecision> findByRunIdOrderByDecidedAtDesc(String runId);
}
