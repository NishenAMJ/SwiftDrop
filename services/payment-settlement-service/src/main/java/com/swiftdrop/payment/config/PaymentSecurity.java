package com.swiftdrop.payment.config;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;
import com.swiftdrop.payment.onlinepayment.controller.ProblemHandler;

@Configuration
public class PaymentSecurity{
    @Bean SecurityFilterChain paymentFilterChain(HttpSecurity http, ProblemHandler problems) throws Exception {
        var grants=new JwtGrantedAuthoritiesConverter(); grants.setAuthoritiesClaimName("roles"); grants.setAuthorityPrefix("ROLE_");
        var converter=new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(grants);
        return http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c->c.disable())
            .authorizeHttpRequests(a->a
                .requestMatchers("/actuator/health","/actuator/health/**","/error").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/payments/callbacks/payhere").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/payments/*/refund").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST,"/api/v1/payments/*/refunds/*/retry").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST,"/api/v1/payments/*/refunds/*/resolve").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.POST,"/api/v1/payments/*/booking-claim/release").hasRole("DISPATCHER")
                .requestMatchers(HttpMethod.GET,"/api/v1/payments/*/recovery-audit").hasRole("DISPATCHER")
                .requestMatchers("/api/v1/payments","/api/v1/payments/**").hasAnyRole("CUSTOMER","MERCHANT","DISPATCHER")
                .anyRequest().denyAll())
            .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(problems).accessDeniedHandler(problems))
            .exceptionHandling(e->e.authenticationEntryPoint(problems).accessDeniedHandler(problems)).build();
    }
}
