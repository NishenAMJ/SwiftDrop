package com.swiftdrop.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Baseline security for the service.
 *
 * <ul>
 *   <li>{@code /actuator/health} is public, for Docker Compose and gateway health checks.</li>
 *   <li>Every other request needs authentication and gets {@code 401} without it.</li>
 *   <li>No HTTP session, no form login, no HTTP Basic: clients authenticate with bearer tokens.</li>
 * </ul>
 *
 * Token issuing and verification (JWT resource server, login endpoints, Google login) are added
 * in the next step, following {@code docs/architecture/authentication.md}.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Stateless bearer-token API: no session cookie for CSRF to abuse. The refresh-cookie
            // endpoints get their own protection when they are added.
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
