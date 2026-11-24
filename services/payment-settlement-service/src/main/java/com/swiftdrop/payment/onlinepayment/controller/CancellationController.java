package com.swiftdrop.payment.onlinepayment.controller;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Page;
import com.swiftdrop.payment.onlinepayment.service.*;

@RestController
@RequestMapping("/api/v1/payments/cancellations")
public class CancellationController {
    private final CancellationService service;
    public CancellationController(CancellationService service) { this.service=service; }
    @GetMapping
    public Page<Map<String,Object>> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@AuthenticationPrincipal Jwt jwt) {
        return service.list(page,size,Actor.from(jwt));
    }
    @GetMapping("/{eventId}")
    public Map<String,Object> get(@PathVariable UUID eventId,@AuthenticationPrincipal Jwt jwt) {
        return service.get(eventId,Actor.from(jwt));
    }
}
