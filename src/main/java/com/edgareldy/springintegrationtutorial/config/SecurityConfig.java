package com.edgareldy.springintegrationtutorial.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Baseline HTTP security: health, the integration graph and the API documentation are public, every
 * other URL requires an authenticated caller. The JWT authentication is plugged in by the auth feature.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
@Configuration
public class SecurityConfig {

    /**
     * @param http the security builder
     * @return the filter chain applied to every request
     * @throws Exception if the chain cannot be built
     */
    // Declaring a SecurityFilterChain bean replaces Spring Boot's default one (HTTP Basic and a
    // generated password on every URL). Health probes must stay reachable without credentials, and a
    // REST API authenticated by tokens needs neither server-side sessions nor CSRF protection, which
    // only defends cookie-based sessions.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /actuator/integrationgraph is only exposed in the dev profile: anywhere else the
                        // request passes this rule and gets a 404, so opening it costs nothing outside dev.
                        .requestMatchers("/actuator/health/**", "/actuator/integrationgraph",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                // Without any login mechanism configured, Spring Security would answer an anonymous
                // caller with a bare 403, the same status as "authenticated but wrong role". 401 keeps
                // "who are you?" distinct from "you may not".
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }
}
