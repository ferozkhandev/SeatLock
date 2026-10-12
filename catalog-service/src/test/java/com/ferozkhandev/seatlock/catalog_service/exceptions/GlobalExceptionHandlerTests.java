package com.ferozkhandev.seatlock.catalog_service.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void unexpectedFailureReturnsGenericProblemWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.type").value("/problems/internal-server-error"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.instance").value("/test/failure"))
                .andExpect(content().string(not(containsString("database-password"))))
                .andExpect(content().string(not(containsString("private-database-host"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void unsupportedMethodUsesFrameworkProblemAndPreservesAllowHeader() throws Exception {
        mockMvc.perform(get("/test/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/body"));
    }

    @Test
    void unsupportedContentTypeUsesFrameworkProblem() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/body"));
    }

    @Test
    void missingQueryParameterUsesFrameworkProblem() throws Exception {
        mockMvc.perform(get("/test/query"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/query"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/failure")
        String fail() {
            throw new IllegalStateException("database-password",
                    new RuntimeException("private-database-host"));
        }

        @PostMapping(value = "/test/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        String body(@RequestBody Payload payload) {
            return payload.name();
        }

        @GetMapping("/test/query")
        String query(@RequestParam("name") String name) {
            return name;
        }
    }

    record Payload(String name) {
    }
}
