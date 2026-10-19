package com.schwab.agentic.urlshortener.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CodeGenerator {

  private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private final SecureRandom secureRandom = new SecureRandom();

  public String generate(int length) {
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int index = secureRandom.nextInt(ALPHABET.length());
      builder.append(ALPHABET.charAt(index));
    }
    return builder.toString();
  }
}
