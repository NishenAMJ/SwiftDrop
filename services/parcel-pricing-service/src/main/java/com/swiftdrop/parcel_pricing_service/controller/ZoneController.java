package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.ZoneRequest;
import com.swiftdrop.parcel_pricing_service.dto.ZoneResponse;
import com.swiftdrop.parcel_pricing_service.dto.ZoneStatusRequest;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.service.ZoneService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    /** Optional {@code ?status=ACTIVE|INACTIVE} filter. */
    @GetMapping
    public List<ZoneResponse> getAllZones(@RequestParam(required = false) ZoneStatus status) {
        return zoneService.getZones(status);
    }

    @GetMapping("/{id}")
    public ZoneResponse getZone(@PathVariable Long id) {
        return zoneService.getZone(id);
    }

    @PostMapping
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ZoneRequest request) {
        ZoneResponse created = zoneService.createZone(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ZoneResponse updateZone(@PathVariable Long id, @Valid @RequestBody ZoneRequest request) {
        return zoneService.updateZone(id, request);
    }

    @PatchMapping("/{id}/status")
    public ZoneResponse updateStatus(@PathVariable Long id, @Valid @RequestBody ZoneStatusRequest request) {
        return zoneService.updateStatus(id, request.status());
    }
}
