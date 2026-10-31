package com.swiftdrop.identity.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth tag of the OpenAPI contract (password flows). All routes here are public. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;

    public AuthController(AuthService authService, RefreshCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return respond(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        return respond(HttpStatus.OK, authService.refresh(cookies.read(request)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookies.read(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    private ResponseEntity<TokenResponse> respond(HttpStatus status, AuthService.AuthResult result) {
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken()))
            .body(result.response());
    }
}
