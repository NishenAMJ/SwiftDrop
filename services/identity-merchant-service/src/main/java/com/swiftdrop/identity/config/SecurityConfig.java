package com.swiftdrop.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.swiftdrop.identity.common.SecurityProblemHandler;

/**
 * Who can call what. Requests carry an access token ({@code Authorization: Bearer ...}) issued by
 * this service; see docs/architecture/authentication.md.
 *
 * <ul>
 *   <li>Public: health, JWKS, register, login, refresh, logout.</li>
 *   <li>Dispatcher only: listing users, creating dispatchers.</li>
 *   <li>Everything else: any valid access token. Controllers then only touch the caller's own data.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemHandler problems) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                        "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                // Spring's error page: lets real status codes through instead of turning them into 401.
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST, "/api/v1/dispatchers").hasRole("DISPATCHER")
                .anyRequest().authenticated())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Bearer tokens aren't sent automatically by browsers, so CSRF doesn't apply to them. The
            // refresh cookie is SameSite=Strict and only sent to /api/v1/auth, which covers that one.
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems));
        return http.build();
    }

    /** BCrypt, stored with a {bcrypt} prefix so the algorithm can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
