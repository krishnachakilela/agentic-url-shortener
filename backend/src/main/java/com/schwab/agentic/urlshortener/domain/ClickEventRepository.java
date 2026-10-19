package com.schwab.agentic.urlshortener.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ClickEventRepository extends JpaRepository<ClickEvent, String> {

  List<ClickEvent> findByShortCodeOrderByOccurredAtDesc(String shortCode);

  @Query("select count(c) from ClickEvent c where c.shortCode = :code and c.occurredAt >= :since")
  long countSince(@Param("code") String shortCode, @Param("since") Instant since);
}
