package com.swiftdrop.identity.user;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.common.PageResponse;
import com.swiftdrop.identity.common.SortGuard;

/**
 * Users tag of the OpenAPI contract. Dispatcher-only routes are restricted in SecurityConfig.
 * (Changing the password lives in the auth package because it also rotates refresh tokens.)
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "email", "fullName");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.get(userId(jwt)));
    }

    @PatchMapping("/users/me")
    UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateMeRequest request) {
        return UserResponse.from(userService.updateProfile(userId(jwt), request.fullName(), request.phoneNumber()));
    }

    @GetMapping("/users")
    PageResponse<UserResponse> list(@RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(name = "q", required = false) String text,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        SortGuard.check(pageable, SORTABLE);
        if (text != null && text.length() > 100) {
            throw ApiException.validation("q", "must be at most 100 characters");
        }
        return PageResponse.of(userService.search(role, status, text, pageable), UserResponse::from);
    }

    @PostMapping("/dispatchers")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse createDispatcher(@Valid @RequestBody CreateDispatcherRequest request) {
        return UserResponse.from(userService.createDispatcher(request.email(), request.fullName(),
                request.phoneNumber(), request.initialPassword()));
    }

    static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
