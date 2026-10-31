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
import com.swiftdrop.identity.token.JwtConfig;

/**
 * Who can call what. Requests carry an access token ({@code Authorization: Bearer ...}) issued by
 * this service; see docs/architecture/authentication.md.
 *
 * <ul>
 *   <li>Public: health, JWKS, register, login, refresh, logout, API-key exchange.</li>
 *   <li>Dispatcher only: user lists, creating dispatchers, merchant/courier lists and decisions.</li>
 *   <li>Customers only: applying as merchant or courier.</li>
 *   <li>Approved merchants (person tokens): managing API keys.</li>
 *   <li>API-key tokens: only {@code GET /api/v1/merchants/me} (see JwtConfig).</li>
 *   <li>Everything else: any signed-in person. Controllers then only touch the caller's own data.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityProblemHandler problems) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                        "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/token").permitAll()
                // Spring's error page: lets real status codes through instead of turning them into 401.
                .requestMatchers("/error").permitAll()

                // The only identity endpoint an API-key token may use.
                .requestMatchers(HttpMethod.GET, "/api/v1/merchants/me").authenticated()

                // Merchant self-service (person tokens only: API-key tokens carry no ROLE_*)
                .requestMatchers("/api/v1/merchants/me/api-keys", "/api/v1/merchants/me/api-keys/**")
                    .hasRole("MERCHANT")

                // Applying: customers only (not dispatchers)
                .requestMatchers(HttpMethod.POST, "/api/v1/merchants", "/api/v1/couriers").hasRole("CUSTOMER")

                // Dispatcher work. The "/me" routes above and below come first, so they aren't caught here.
                .requestMatchers(HttpMethod.GET, "/api/v1/couriers/me").hasAuthority(JwtConfig.PERSON_AUTHORITY)
                .requestMatchers(HttpMethod.GET, "/api/v1/users").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST, "/api/v1/dispatchers").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.GET, "/api/v1/merchants", "/api/v1/merchants/*").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST, "/api/v1/merchants/*/*").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.GET, "/api/v1/couriers", "/api/v1/couriers/*").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST, "/api/v1/couriers/*/*").hasRole("DISPATCHER")

                // Everything else (own account, password): any signed-in person.
                .anyRequest().hasAuthority(JwtConfig.PERSON_AUTHORITY))
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
