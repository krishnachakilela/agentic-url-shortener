package com.schwab.agentic.orchestration.application;

import com.schwab.agentic.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ArtifactStore {

  private final Path root;

  public ArtifactStore(AppProperties appProperties) throws IOException {
    this.root = Path.of(appProperties.orchestration().artifactsDir()).toAbsolutePath().normalize();
    Files.createDirectories(this.root);
  }

  public String write(String runId, String fileName, String content) {
    try {
      Path runDir = root.resolve(runId);
      Files.createDirectories(runDir);
      Path target = runDir.resolve(fileName).normalize();
      if (!target.startsWith(runDir)) {
        throw new IllegalArgumentException("Invalid artifact path");
      }
      Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
      return target.toString();
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to persist artifact", ex);
    }
  }
}
