package com.schwab.agentic.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
  List<AuditEvent> findByRunIdOrderByOccurredAtAsc(String runId);
}
