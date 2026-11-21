package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.DispatchParcelDto;
import com.swiftdrop.parcel_pricing_service.service.DispatchParcelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dispatcher-only parcel lookup. Customers use /api/v1/bookings; everyone else uses public tracking. */
@RestController
@RequestMapping("/api/v1/dispatch/parcels")
public class DispatchParcelController {

    private final DispatchParcelService service;

    public DispatchParcelController(DispatchParcelService service) {
        this.service = service;
    }

    @GetMapping("/track/{trackingNumber}")
    public DispatchParcelDto byTrackingNumber(@PathVariable String trackingNumber) {
        return service.findByTrackingNumber(trackingNumber);
    }
}
