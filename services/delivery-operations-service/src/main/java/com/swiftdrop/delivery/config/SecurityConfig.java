package com.swiftdrop.delivery.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Access tokens are issued by identity-merchant-service; roles come from the "roles" claim.
 * Role rules live here. Ownership (a courier sees only their own work) is checked in the controllers
 * through CourierAccess.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()

                        // Warehouse operations: dispatchers only.
                        .requestMatchers("/api/v1/warehouses", "/api/v1/warehouses/**",
                                "/api/v1/warehouse-parcels/**", "/api/v1/sorting-records/**",
                                "/api/v1/transfers", "/api/v1/transfers/**").hasRole("DISPATCHER")

                        // Courier categories: everyone signed in as courier or dispatcher may read.
                        .requestMatchers(HttpMethod.GET, "/api/v1/courier-categories",
                                "/api/v1/courier-categories/*").hasAnyRole("COURIER", "DISPATCHER")
                        .requestMatchers("/api/v1/courier-categories", "/api/v1/courier-categories/**")
                        .hasRole("DISPATCHER")

                        // Courier profiles: a courier may look up their own profile.
                        .requestMatchers(HttpMethod.GET, "/api/v1/courier-profiles/by-identity/*")
                        .hasAnyRole("COURIER", "DISPATCHER")
                        .requestMatchers("/api/v1/courier-profiles", "/api/v1/courier-profiles/**")
                        .hasRole("DISPATCHER")

                        // Assignments: dispatchers assign, the courier moves their own parcel along.
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/assignments/*/status").hasRole("COURIER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/assignments/*/attempts",
                                "/api/v1/assignments/*/proof").hasRole("COURIER")
                        // The audit trail names staff by identity id, so couriers do not read it.
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignments/*/history").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assignments", "/api/v1/assignments/**")
                        .hasAnyRole("COURIER", "DISPATCHER")

                        // Issues: couriers report, dispatchers review.
                        .requestMatchers(HttpMethod.POST, "/api/v1/delivery-issues").hasRole("COURIER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/delivery-issues/*/review")
                        .hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/delivery-issues",
                                "/api/v1/delivery-issues/*").hasAnyRole("COURIER", "DISPATCHER")

                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
