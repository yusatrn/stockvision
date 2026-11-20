package com.stockvision.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.auth.domain.AuthRateLimiter;
import com.stockvision.auth.infrastructure.JwtProperties;
import com.stockvision.auth.infrastructure.JwtTokenService;
import com.stockvision.auth.infrastructure.RateLimitProperties;
import com.stockvision.auth.infrastructure.SecurityPasswordProperties;
import com.stockvision.auth.web.AuthRateLimitFilter;
import com.stockvision.auth.web.JwtAuthenticationFilter;
import com.stockvision.common.api.ApiErrorResponse;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.common.web.TraceIdFilter;
import com.stockvision.user.domain.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless security baseline with JWT authentication, auth rate limiting, explicit CORS
 * configuration, and consistent JSON error responses.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({
    CorsProperties.class,
    JwtProperties.class,
    SecurityPasswordProperties.class,
    RateLimitProperties.class
})
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder(SecurityPasswordProperties passwordProperties) {
        passwordProperties.validate();
        return new BCryptPasswordEncoder(passwordProperties.getBcryptStrength());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            ObjectMapper objectMapper,
            Clock clock,
            JwtTokenService jwtTokenService,
            UserRepository userRepository,
            AuthRateLimiter authRateLimiter,
            RateLimitProperties rateLimitProperties,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled)
            throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtTokenService, userRepository);
        AuthRateLimitFilter authRateLimitFilter =
                new AuthRateLimitFilter(authRateLimiter, rateLimitProperties, objectMapper, clock);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(
                        headers ->
                                headers.contentTypeOptions(Customizer.withDefaults())
                                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                                        .cacheControl(Customizer.withDefaults())
                                        .referrerPolicy(
                                                ref ->
                                                        ref.policy(
                                                                ReferrerPolicyHeaderWriter
                                                                        .ReferrerPolicy.NO_REFERRER))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "Permissions-Policy",
                                                        "camera=(), microphone=(), geolocation=()")))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, authException) ->
                                                        writeErrorResponse(
                                                                request,
                                                                response,
                                                                ErrorCode.UNAUTHORIZED,
                                                                "Authentication required",
                                                                objectMapper,
                                                                clock))
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) ->
                                                        writeErrorResponse(
                                                                request,
                                                                response,
                                                                ErrorCode.FORBIDDEN,
                                                                "Access denied",
                                                                objectMapper,
                                                                clock)))
                .authorizeHttpRequests(
                        auth -> {
                            auth.requestMatchers(
                                            HttpMethod.GET,
                                            "/api/v1/health",
                                            "/actuator/health",
                                            "/actuator/health/**",
                                            "/actuator/info")
                                    .permitAll();
                            auth.requestMatchers(
                                            HttpMethod.POST,
                                            "/api/v1/auth/register",
                                            "/api/v1/auth/login",
                                            "/api/v1/auth/refresh",
                                            "/api/v1/auth/logout")
                                    .permitAll();
                            if (apiDocsEnabled) {
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/v3/api-docs",
                                                "/v3/api-docs/**",
                                                "/v3/api-docs.yaml")
                                        .permitAll();
                            }
                            if (swaggerUiEnabled) {
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll();
                            }
                            auth.anyRequest().authenticated();
                        })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        corsProperties.validate();
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = corsProperties.getSanitizedAllowedOrigins();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private static void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode,
            String message,
            ObjectMapper objectMapper,
            Clock clock)
            throws IOException {
        ApiErrorResponse errorResponse =
                ApiErrorResponse.of(
                        Instant.now(clock),
                        errorCode.getStatus().value(),
                        errorCode.getCode(),
                        message,
                        request.getRequestURI(),
                        TraceIdFilter.resolveCurrentTraceId(request));
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
