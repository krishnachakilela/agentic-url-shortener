package com.schwab.agentic.orchestration.application;

import com.schwab.agentic.orchestration.domain.AuditEvent;
import com.schwab.agentic.orchestration.domain.AuditEventRepository;
import com.schwab.agentic.orchestration.policy.PolicyGuardrails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

  private final AuditEventRepository auditEventRepository;
  private final PolicyGuardrails policyGuardrails;

  public AuditService(AuditEventRepository auditEventRepository, PolicyGuardrails policyGuardrails) {
    this.auditEventRepository = auditEventRepository;
    this.policyGuardrails = policyGuardrails;
  }

  @Transactional
  public void record(String runId, String stageKey, String eventType, String actor, String detailJson) {
    String safeDetail = policyGuardrails.redact(detailJson);
    auditEventRepository.save(new AuditEvent(runId, stageKey, eventType, actor, safeDetail));
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> lineage(String runId) {
    return auditEventRepository.findByRunIdOrderByOccurredAtAsc(runId);
  }
}
