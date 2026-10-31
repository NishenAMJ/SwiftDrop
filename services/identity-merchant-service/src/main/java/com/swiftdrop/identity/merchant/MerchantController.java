package com.swiftdrop.identity.merchant;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.approval.ApplicationAction;
import com.swiftdrop.identity.approval.ApplicationStatus;
import com.swiftdrop.identity.approval.NoteRequest;
import com.swiftdrop.identity.approval.ReasonRequest;
import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.common.PageResponse;
import com.swiftdrop.identity.common.SortGuard;

/** Merchants tag of the OpenAPI contract. Who may call what is set in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt", "businessName");

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MerchantResponse apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MerchantApplicationRequest request) {
        return merchantService.apply(userId(jwt), request).forApplicant();
    }

    @GetMapping("/me")
    MerchantResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return merchantService.mine(userId(jwt)).forApplicant();
    }

    @GetMapping
    PageResponse<MerchantResponse> list(@RequestParam(required = false) ApplicationStatus status,
            @RequestParam(name = "q", required = false) String text,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        SortGuard.check(pageable, SORTABLE);
        if (text != null && text.length() > 100) {
            throw ApiException.validation("q", "must be at most 100 characters");
        }
        return PageResponse.of(merchantService.search(status, text, pageable), m -> m);
    }

    @GetMapping("/{merchantId}")
    MerchantResponse get(@PathVariable UUID merchantId) {
        return merchantService.get(merchantId);
    }

    @PostMapping("/{merchantId}/approve")
    MerchantResponse approve(@PathVariable UUID merchantId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) NoteRequest body) {
        return merchantService.decide(merchantId, ApplicationAction.APPROVE, userId(jwt), null, note(body));
    }

    @PostMapping("/{merchantId}/reject")
    MerchantResponse reject(@PathVariable UUID merchantId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest body) {
        return merchantService.decide(merchantId, ApplicationAction.REJECT, userId(jwt), body.reason().trim(), null);
    }

    @PostMapping("/{merchantId}/suspend")
    MerchantResponse suspend(@PathVariable UUID merchantId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest body) {
        return merchantService.decide(merchantId, ApplicationAction.SUSPEND, userId(jwt), body.reason().trim(), null);
    }

    @PostMapping("/{merchantId}/reactivate")
    MerchantResponse reactivate(@PathVariable UUID merchantId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) NoteRequest body) {
        return merchantService.decide(merchantId, ApplicationAction.REACTIVATE, userId(jwt), null, note(body));
    }

    private static String note(NoteRequest body) {
        return body == null || body.note() == null || body.note().isBlank() ? null : body.note().trim();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
