package com.schwab.agentic.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UrlShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void test_create_short_url_valid_input_returns_created() throws Exception {
        // Arrange
        String body = """
                {"url":"https://example.com/docs/agentic"}
                """;

        // Act / Assert
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/docs/agentic"));
    }

    @Test
    void test_create_short_url_invalid_scheme_returns_bad_request() throws Exception {
        // Arrange
        String body = """
                {"url":"javascript:alert(1)"}
                """;

        // Act / Assert
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void test_redirect_tracks_click_and_returns_found() throws Exception {
        // Arrange
        MvcResult created = mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/landing\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String response = created.getResponse().getContentAsString();
        String code = response.replaceAll("(?s).*\"shortCode\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(code).isNotBlank().doesNotContain("{");

        // Act
        mockMvc.perform(get("/r/" + code))
                .andExpect(status().isFound());

        // Assert
        mockMvc.perform(get("/api/v1/urls/" + code + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1));
    }
}
