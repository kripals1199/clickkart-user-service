// src/main/java/com/clickkart/user/config/SecurityConfig.java
package com.clickkart.user.config;

import com.clickkart.user.constant.ApiPaths;
import com.clickkart.user.jwt.JwtAuthenticationFilter;
import com.clickkart.user.jwt.JwtService;
import com.clickkart.user.security.AuthenticatedPrincipal;
import com.clickkart.user.security.RestAccessDeniedHandler;
import com.clickkart.user.security.RestAuthenticationEntryPoint;
import com.clickkart.user.security.InternalApiKeyFilter;
import com.clickkart.user.security.RevocationService;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * This service validates every access token itself via {@link JwtAuthenticationFilter} rather than
 * trusting Gateway-forwarded headers - see that class's Javadoc for why that distinction is the
 * difference between a working authorization boundary and an impersonation hole.
 *
 * <p>Unlike Auth Service there is no password encoder, no {@code AuthenticationManager} and no
 * {@code UserDetailsService}: this service never authenticates a credential, only verifies a token
 * someone else issued. Sessions are stateless (JWT end-to-end, Rule 1); CSRF is disabled since
 * there is no cookie-based session to protect.
 *
 * <p>{@link #PUBLIC_PATHS} contains no business route. Every profile and address endpoint requires
 * a token, including reads - an address book is personal data, not public catalog content.
 */
@Configuration
@EnableConfigurationProperties(UserProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    /** Only used if a write ever occurs outside a request context; every real path here is authenticated. */
    private static final String SYSTEM_ACTOR = "system";

    /** Genuinely unauthenticated. Contains no business route - profile and address data is personal. */
    private static final List<String> PUBLIC_PATHS = List.of(
            ApiPaths.ACTUATOR_HEALTH,
            ApiPaths.ACTUATOR_HEALTH_WILDCARD,
            ApiPaths.ACTUATOR_PROMETHEUS,
            ApiPaths.SWAGGER_UI,
            ApiPaths.SWAGGER_UI_WILDCARD,
            ApiPaths.API_DOCS_WILDCARD);

    /** Authenticated by shared secret instead of a JWT - see {@link InternalApiKeyFilter}. */
    private static final List<String> INTERNAL_PATHS = List.of(ApiPaths.INTERNAL_WILDCARD);

    /**
     * Paths {@link JwtAuthenticationFilter} must not try to authenticate. This is deliberately a
     * different list from {@link #PUBLIC_PATHS}: "the JWT filter skips this" and "anyone may call
     * this" are separate questions, and conflating them is how an internal endpoint accidentally
     * becomes an anonymous one. The internal paths skip the JWT filter because they carry no
     * bearer token, and are then authorized against ROLE_INTERNAL below.
     */
    private static final List<String> JWT_EXEMPT_PATHS =
            Stream.concat(PUBLIC_PATHS.stream(), INTERNAL_PATHS.stream()).toList();

    /** {@code HstsHeaderWriter.DEFAULT_MAX_AGE_SECONDS} is private - this is the same 365-day value, just accessible. */
    private static final long HSTS_MAX_AGE_SECONDS = Duration.ofDays(365).toSeconds();

    /** Stateless JSON API - no inline scripts, no framing, no third-party resource loading. */
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'";

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            RevocationService revocationService,
            HandlerExceptionResolver handlerExceptionResolver) {
        return new JwtAuthenticationFilter(jwtService, revocationService, handlerExceptionResolver, JWT_EXEMPT_PATHS);
    }

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter(
            UserProperties userProperties, HandlerExceptionResolver handlerExceptionResolver) {
        return new InternalApiKeyFilter(
                userProperties.getInternalApiKey(), handlerExceptionResolver, INTERNAL_PATHS);
    }

    /** Defense in depth - this service is independently reachable, bypassing the Gateway's own CORS config. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(UserProperties userProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.stream(userProperties.getAllowedOrigins().split(",")).map(String::trim).toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalApiKeyFilter internalApiKeyFilter,
            CorsConfigurationSource corsConfigurationSource,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS.toArray(new String[0]))
                        .permitAll()
                        // Belt and braces: InternalApiKeyFilter already rejects a bad key, but this
                        // means a future filter-ordering mistake fails closed rather than leaving
                        // the internal surface anonymous.
                        .requestMatchers(INTERNAL_PATHS.toArray(new String[0]))
                        .hasRole("INTERNAL")
                        .anyRequest()
                        .authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter, JwtAuthenticationFilter.class)
                // Safety net only - see RestAuthenticationEntryPoint/RestAccessDeniedHandler Javadoc.
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // Explicit rather than relying silently on Spring Security's own defaults (Rule 14),
                // even where these match what it applies out of the box.
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .cacheControl(cacheControl -> {})
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)));
        return http.build();
    }

    /**
     * createdBy/updatedBy source (Rule 3) - the authenticated principal's publicId, never a
     * client-supplied value.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal())
                .filter(AuthenticatedPrincipal.class::isInstance)
                .map(AuthenticatedPrincipal.class::cast)
                .map(AuthenticatedPrincipal::userId)
                .or(() -> Optional.of(SYSTEM_ACTOR));
    }
}
