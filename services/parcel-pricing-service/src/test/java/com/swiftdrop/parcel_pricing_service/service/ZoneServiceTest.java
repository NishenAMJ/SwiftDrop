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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @InjectMocks
    private ZoneService zoneService;

    private static DeliveryZone zone(Long id, String name, String code, ZoneStatus status) {
        DeliveryZone z = new DeliveryZone(name, code, "desc", ZoneType.DISTRICT);
        z.setStatus(status);
        ReflectionTestUtils.setField(z, "id", id);
        return z;
    }

    // ---------- create ----------

    @Test
    void createZone_normalizesInputAndDefaultsToActiveGeneral() {
        when(zoneRepository.existsByCode("COL")).thenReturn(false);
        when(zoneRepository.existsByNameIgnoreCase("Colombo")).thenReturn(false);
        when(zoneRepository.saveAndFlush(any(DeliveryZone.class))).thenAnswer(inv -> {
            DeliveryZone z = inv.getArgument(0);
            ReflectionTestUtils.setField(z, "id", 1L);
            return z;
        });

        ZoneResponse response = zoneService.createZone(new ZoneRequest("  Colombo ", " col ", "  ", null));

        ArgumentCaptor<DeliveryZone> captor = ArgumentCaptor.forClass(DeliveryZone.class);
        verify(zoneRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Colombo");
        assertThat(captor.getValue().getCode()).isEqualTo("COL");
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(ZoneStatus.ACTIVE);
        assertThat(response.zoneType()).isEqualTo(ZoneType.GENERAL);
    }

    @Test
    void createZone_duplicateCode_throwsConflict() {
        when(zoneRepository.existsByCode("COL")).thenReturn(true);

        assertThatThrownBy(() -> zoneService.createZone(new ZoneRequest("Colombo", "col", null, null)))
                .isInstanceOf(DuplicateZoneException.class)
                .hasMessageContaining("COL");
        verify(zoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void createZone_duplicateNameIgnoringCase_throwsConflict() {
        when(zoneRepository.existsByCode("COL2")).thenReturn(false);
        when(zoneRepository.existsByNameIgnoreCase("COLOMBO")).thenReturn(true);

        assertThatThrownBy(() -> zoneService.createZone(new ZoneRequest("COLOMBO", "COL2", null, null)))
                .isInstanceOf(DuplicateZoneException.class);
        verify(zoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void createZone_raceOnUniqueConstraint_isTranslatedToDuplicate() {
        when(zoneRepository.saveAndFlush(any(DeliveryZone.class)))
                .thenThrow(new DataIntegrityViolationException("uq_delivery_zones_code"));

        assertThatThrownBy(() -> zoneService.createZone(new ZoneRequest("Kandy", "KAN", null, null)))
                .isInstanceOf(DuplicateZoneException.class);
    }

    // ---------- read ----------

    @Test
    void getZone_unknownId_throwsNotFound() {
        when(zoneRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneService.getZone(99L))
                .isInstanceOf(ZoneNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getZones_withoutFilter_returnsAll() {
        when(zoneRepository.findAll(any(Sort.class))).thenReturn(List.of(
                zone(1L, "Colombo", "COL", ZoneStatus.ACTIVE),
                zone(2L, "Kandy", "KAN", ZoneStatus.INACTIVE)));

        assertThat(zoneService.getZones(null)).extracting(ZoneResponse::code).containsExactly("COL", "KAN");
    }

    @Test
    void getZones_withStatusFilter_usesFilteredQuery() {
        when(zoneRepository.findByStatus(any(ZoneStatus.class), any(Sort.class)))
                .thenReturn(List.of(zone(1L, "Colombo", "COL", ZoneStatus.ACTIVE)));

        assertThat(zoneService.getZones(ZoneStatus.ACTIVE)).hasSize(1);
        verify(zoneRepository, never()).findAll(any(Sort.class));
    }

    // ---------- update ----------

    @Test
    void updateZone_updatesFields() {
        DeliveryZone existing = zone(1L, "Colombo", "COL", ZoneStatus.ACTIVE);
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(zoneRepository.existsByCodeAndIdNot("CMB", 1L)).thenReturn(false);
        when(zoneRepository.existsByNameIgnoreCaseAndIdNot("Colombo City", 1L)).thenReturn(false);
        when(zoneRepository.saveAndFlush(existing)).thenReturn(existing);

        ZoneResponse response = zoneService.updateZone(1L,
                new ZoneRequest("Colombo City", "cmb", " Urban core ", ZoneType.DISTRICT));

        assertThat(response.name()).isEqualTo("Colombo City");
        assertThat(response.code()).isEqualTo("CMB");
        assertThat(response.description()).isEqualTo("Urban core");
    }

    @Test
    void updateZone_codeUsedByAnotherZone_throwsConflict() {
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone(1L, "Colombo", "COL", ZoneStatus.ACTIVE)));
        when(zoneRepository.existsByCodeAndIdNot("KAN", 1L)).thenReturn(true);

        assertThatThrownBy(() -> zoneService.updateZone(1L, new ZoneRequest("Colombo", "KAN", null, null)))
                .isInstanceOf(DuplicateZoneException.class);
        verify(zoneRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateZone_unknownId_throwsNotFound() {
        when(zoneRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneService.updateZone(5L, new ZoneRequest("X Zone", "XZ", null, null)))
                .isInstanceOf(ZoneNotFoundException.class);
    }

    // ---------- status ----------

    @Test
    void updateStatus_deactivatesWithoutDeleting() {
        DeliveryZone existing = zone(1L, "Colombo", "COL", ZoneStatus.ACTIVE);
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(zoneRepository.save(existing)).thenReturn(existing);

        ZoneResponse response = zoneService.updateStatus(1L, ZoneStatus.INACTIVE);

        assertThat(response.status()).isEqualTo(ZoneStatus.INACTIVE);
        verify(zoneRepository, never()).delete(any());
        verify(zoneRepository, never()).deleteById(any());
    }

    @Test
    void updateStatus_unknownId_throwsNotFound() {
        when(zoneRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneService.updateStatus(7L, ZoneStatus.ACTIVE))
                .isInstanceOf(ZoneNotFoundException.class);
    }

    // ---------- pricing integration ----------

    @Test
    void requireActiveZone_activeZone_returnsIt() {
        when(zoneRepository.findById(1L)).thenReturn(Optional.of(zone(1L, "Colombo", "COL", ZoneStatus.ACTIVE)));

        assertThat(zoneService.requireActiveZone(1L).code()).isEqualTo("COL");
    }

    @Test
    void requireActiveZone_inactiveZone_throws() {
        when(zoneRepository.findById(2L)).thenReturn(Optional.of(zone(2L, "Kandy", "KAN", ZoneStatus.INACTIVE)));

        assertThatThrownBy(() -> zoneService.requireActiveZone(2L)).isInstanceOf(ZoneInactiveException.class);
    }
}
