package com.schwab.agentic.urlshortener;

import com.schwab.agentic.urlshortener.application.UrlValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator urlValidator = new UrlValidator();

    @Test
    void test_validate_target_url_https_ok() {
        // Arrange / Act / Assert
        assertThatCode(() -> urlValidator.validateTargetUrl("https://example.com/a", 2048))
                .doesNotThrowAnyException();
    }

    @Test
    void test_validate_target_url_localhost_rejected() {
        // Arrange / Act / Assert
        assertThatThrownBy(() -> urlValidator.validateTargetUrl("http://localhost/admin", 2048))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
