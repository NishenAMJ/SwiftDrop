package com.swiftdrop.identity.google;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import com.swiftdrop.identity.auth.AuthService;
import com.swiftdrop.identity.auth.RefreshCookies;
import com.swiftdrop.identity.config.IdentityProperties;

/**
 * Google login (OpenID Connect). Active only when GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set;
 * without them, /oauth2/authorization/google is just an unknown, protected path.
 *
 * <p>This chain handles only the two Google URLs and runs before the main API chain. It needs a
 * short-lived HTTP session to remember the login request between the redirect to Google and the
 * callback (Spring's default); the session is ended when the login finishes. Nothing else in the
 * service uses sessions. With several instances, the callback must reach the same instance
 * (sticky sessions) until this is replaced by a cookie-based store.
 */
@Configuration
@ConditionalOnExpression("!'${swiftdrop.identity.google.client-id:}'.isBlank() and !'${swiftdrop.identity.google.client-secret:}'.isBlank()")
public class GoogleLoginConfig {

    static final String REGISTRATION_ID = "google";

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(IdentityProperties properties) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder(REGISTRATION_ID)
            .clientId(properties.google().clientId())
            .clientSecret(properties.google().clientSecret())
            .scope("openid", "email", "profile")
            .build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain googleLoginChain(HttpSecurity http, GoogleLoginHandler handler) throws Exception {
        http
            .securityMatcher("/oauth2/authorization/**", "/login/oauth2/code/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2Login(login -> login.successHandler(handler).failureHandler(handler))
            // The result of Google login is our refresh cookie, not a logged-in session.
            .securityContext(context -> context.securityContextRepository(new RequestAttributeSecurityContextRepository()));
        return http.build();
    }

    @Bean
    GoogleLoginHandler googleLoginHandler(AuthService authService, RefreshCookies cookies,
            IdentityProperties properties) {
        return new GoogleLoginHandler(authService, cookies, properties.frontendBaseUrl());
    }
}
