package com.schwab.agentic.orchestration.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageInstanceRepository extends JpaRepository<StageInstance, String> {
  List<StageInstance> findByRunIdOrderBySequenceOrderAsc(String runId);

  Optional<StageInstance> findByRunIdAndStageKey(String runId, String stageKey);

  List<StageInstance> findByRunIdAndStatus(String runId, StageStatus status);
}
