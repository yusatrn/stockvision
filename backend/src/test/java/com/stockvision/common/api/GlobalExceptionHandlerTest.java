package com.stockvision.common.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockvision.TestSecuritySupport;
import com.stockvision.auth.infrastructure.InMemoryAuthRateLimiter;
import com.stockvision.auth.infrastructure.JwtTokenService;
import com.stockvision.common.config.SecurityConfig;
import com.stockvision.common.config.TimeConfig;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.common.web.TraceIdFilter;
import com.stockvision.user.domain.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({
    GlobalExceptionHandler.class,
    TraceIdFilter.class,
    TimeConfig.class,
    SecurityConfig.class,
    InMemoryAuthRateLimiter.class,
    GlobalExceptionHandlerTest.ProbeController.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:3000")
@WithMockUser
class GlobalExceptionHandlerTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        TestSecuritySupport.registerJwtSecret(registry);
    }

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStructuredFieldErrorsForValidationFailure() throws Exception {
        mockMvc.perform(
                        post("/test/validation")
                                .header(TraceIdFilter.HEADER_NAME, "trace-validation-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"symbol": "", "price": -5}
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(TraceIdFilter.HEADER_NAME, "trace-validation-1"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.traceId").value("trace-validation-1"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("symbol"));
    }

    @Test
    void returnsMalformedRequestForInvalidJson() throws Exception {
        mockMvc.perform(
                        post("/test/validation")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void returnsDomainErrorCodeForAppException() throws Exception {
        mockMvc.perform(get("/test/insufficient"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.message").value("Not enough historical bars"))
                .andExpect(jsonPath("$.path").value("/test/insufficient"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void returnsNotFoundForUnknownPath() throws Exception {
        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/test/does-not-exist"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void hidesInternalExceptionDetailsOnUnhandledFailure() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.message", not(containsString("secret-db-password-leak"))));
    }

    @RestController
    static class ProbeController {

        @PostMapping("/test/validation")
        public String validate(@Valid @RequestBody ProbeRequest request) {
            return request.symbol();
        }

        @GetMapping("/test/insufficient")
        public String insufficient() {
            throw new AppException(ErrorCode.INSUFFICIENT_DATA, "Not enough historical bars");
        }

        @GetMapping("/test/boom")
        public String boom() {
            throw new IllegalStateException("secret-db-password-leak");
        }
    }

    record ProbeRequest(@NotBlank String symbol, @Positive BigDecimal price) {
    }
}
