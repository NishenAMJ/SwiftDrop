package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.ZoneRequest;
import com.swiftdrop.parcel_pricing_service.dto.ZoneResponse;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryZone;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.entity.ZoneType;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateZoneException;
import com.swiftdrop.parcel_pricing_service.exception.ZoneInactiveException;
import com.swiftdrop.parcel_pricing_service.exception.ZoneNotFoundException;
import com.swiftdrop.parcel_pricing_service.repository.ZoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Business logic for delivery zones.
 *
 * <p>The pricing module lives in the same service, so it should call
 * {@link #requireActiveZone(Long)} / {@link #getZone(Long)} instead of touching
 * {@link ZoneRepository} directly. Other microservices must use the REST API.
 */
@Service
@Transactional(readOnly = true)
public class ZoneService {

    private static final Logger log = LoggerFactory.getLogger(ZoneService.class);
    private static final Sort BY_NAME = Sort.by("name");

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    /** @param status optional filter; {@code null} returns every zone */
    public List<ZoneResponse> getZones(ZoneStatus status) {
        List<DeliveryZone> zones = (status == null)
                ? zoneRepository.findAll(BY_NAME)
                : zoneRepository.findByStatus(status, BY_NAME);
        return zones.stream().map(ZoneResponse::from).toList();
    }

    public ZoneResponse getZone(Long id) {
        return ZoneResponse.from(findOrThrow(id));
    }

    /**
     * For the pricing module: returns the zone only if it exists and is ACTIVE.
     *
     * @throws ZoneNotFoundException if the id is unknown
     * @throws ZoneInactiveException if the zone is deactivated
     */
    public ZoneResponse requireActiveZone(Long id) {
        DeliveryZone zone = findOrThrow(id);
        if (zone.getStatus() != ZoneStatus.ACTIVE) {
            throw new ZoneInactiveException(id);
        }
        return ZoneResponse.from(zone);
    }

    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        String name = request.name().trim();
        String code = normalizeCode(request.code());

        if (zoneRepository.existsByCode(code)) {
            throw new DuplicateZoneException("A delivery zone with code '" + code + "' already exists");
        }
        if (zoneRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateZoneException("A delivery zone with this name already exists");
        }

        DeliveryZone zone = new DeliveryZone(name, code, trimToNull(request.description()),
                request.zoneType() != null ? request.zoneType() : ZoneType.GENERAL);

        DeliveryZone saved = saveAndTranslate(zone);
        log.info("Created delivery zone id={} code={}", saved.getId(), saved.getCode());
        return ZoneResponse.from(saved);
    }

    @Transactional
    public ZoneResponse updateZone(Long id, ZoneRequest request) {
        DeliveryZone zone = findOrThrow(id);
        String name = request.name().trim();
        String code = normalizeCode(request.code());

        if (zoneRepository.existsByCodeAndIdNot(code, id)) {
            throw new DuplicateZoneException("A delivery zone with code '" + code + "' already exists");
        }
        if (zoneRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateZoneException("A delivery zone with this name already exists");
        }

        zone.setName(name);
        zone.setCode(code);
        zone.setDescription(trimToNull(request.description()));
        if (request.zoneType() != null) {
            zone.setZoneType(request.zoneType());
        }

        DeliveryZone saved = saveAndTranslate(zone);
        log.info("Updated delivery zone id={} code={}", saved.getId(), saved.getCode());
        return ZoneResponse.from(saved);
    }

    /** Activates or deactivates a zone. Records are never deleted. */
    @Transactional
    public ZoneResponse updateStatus(Long id, ZoneStatus status) {
        DeliveryZone zone = findOrThrow(id);
        ZoneStatus previous = zone.getStatus();
        zone.setStatus(status);
        DeliveryZone saved = zoneRepository.save(zone);
        log.info("Delivery zone id={} status {} -> {}", id, previous, status);
        return ZoneResponse.from(saved);
    }

    private DeliveryZone findOrThrow(Long id) {
        return zoneRepository.findById(id).orElseThrow(() -> new ZoneNotFoundException(id));
    }

    /** Flushes so a concurrent duplicate surfaces here as a clean 409 instead of at commit. */
    private DeliveryZone saveAndTranslate(DeliveryZone zone) {
        try {
            return zoneRepository.saveAndFlush(zone);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateZoneException("A delivery zone with this name or code already exists", ex);
        }
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
