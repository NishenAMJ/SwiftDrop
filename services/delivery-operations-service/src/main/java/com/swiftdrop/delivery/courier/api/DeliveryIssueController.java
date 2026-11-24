package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.IssueStatus;
import com.swiftdrop.delivery.courier.security.CourierAccess;
import com.swiftdrop.delivery.courier.service.DeliveryIssueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery-issues")
public class DeliveryIssueController {

    private final DeliveryIssueService issueService;
    private final CourierAccess access;

    public DeliveryIssueController(DeliveryIssueService issueService, CourierAccess access) {
        this.issueService = issueService;
        this.access = access;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse report(@Valid @RequestBody ReportIssueRequest request, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwner(jwt, request.courierId());
        return issueService.report(request);
    }

    @GetMapping
    public Page<IssueResponse> list(@RequestParam(required = false) UUID courierId,
                                    @RequestParam(required = false) IssueStatus status,
                                    @PageableDefault(size = 20, sort = "reportedAt") Pageable pageable,
                                    @AuthenticationPrincipal Jwt jwt) {
        return issueService.list(access.scopeToCaller(jwt, courierId), status, pageable);
    }

    @GetMapping("/{issueId}")
    public IssueResponse get(@PathVariable UUID issueId, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwnerOrDispatcher(jwt, issueService.courierIdOf(issueId));
        return issueService.get(issueId);
    }

    @PatchMapping("/{issueId}/review")
    public IssueResponse review(@PathVariable UUID issueId, @Valid @RequestBody ReviewIssueRequest request) {
        return issueService.review(issueId, request);
    }
}
