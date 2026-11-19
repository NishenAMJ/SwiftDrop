package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.dto.ZoneResponse;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import com.swiftdrop.parcel_pricing_service.entity.ZoneType;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateZoneException;
import com.swiftdrop.parcel_pricing_service.exception.ZoneExceptionHandler;
import com.swiftdrop.parcel_pricing_service.exception.ZoneNotFoundException;
import com.swiftdrop.parcel_pricing_service.service.ZoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests (no Spring context), so they do not depend on the
 * Spring Boot version's test-slice annotations.
 */
@ExtendWith(MockitoExtension.class)
class ZoneControllerTest {

    @Mock
    private ZoneService zoneService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ZoneController(zoneService))
                .setControllerAdvice(new ZoneExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static ZoneResponse colombo() {
        Instant now = Instant.parse("2026-11-14T06:30:00Z");
        return new ZoneResponse(1L, "Colombo", "COL", "Western Province capital",
                ZoneType.DISTRICT, ZoneStatus.ACTIVE, now, now);
    }

    @Test
    void getAll_returnsList() throws Exception {
        when(zoneService.getZones(null)).thenReturn(List.of(colombo()));

        mockMvc.perform(get("/api/zones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("COL"));
    }

    @Test
    void getAll_withInvalidStatusFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/zones").param("status", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'status' has an invalid value"));
    }

    @Test
    void getById_unknown_returns404WithMessage() throws Exception {
        when(zoneService.getZone(99L)).thenThrow(new ZoneNotFoundException(99L));

        mockMvc.perform(get("/api/zones/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Delivery zone with id 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/zones/99"));
    }

    @Test
    void getById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/zones/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value"));
    }

    @Test
    void create_valid_returns201WithLocation() throws Exception {
        when(zoneService.createZone(any())).thenReturn(colombo());

        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Colombo","code":"COL","description":"Western Province capital","zoneType":"DISTRICT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/zones/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_invalid_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"a b!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.code").exists());

        verify(zoneService, never()).createZone(any());
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        when(zoneService.createZone(any()))
                .thenThrow(new DuplicateZoneException("A delivery zone with code 'COL' already exists"));

        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Colombo\",\"code\":\"COL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A delivery zone with code 'COL' already exists"));
    }

    @Test
    void create_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invalidZoneType_returns400() throws Exception {
        mockMvc.perform(post("/api/zones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Colombo\",\"code\":\"COL\",\"zoneType\":\"GALAXY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_valid_returns200() throws Exception {
        when(zoneService.updateZone(eq(1L), any())).thenReturn(colombo());

        mockMvc.perform(put("/api/zones/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Colombo\",\"code\":\"COL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COL"));
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        when(zoneService.updateZone(eq(42L), any())).thenThrow(new ZoneNotFoundException(42L));

        mockMvc.perform(put("/api/zones/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Colombo\",\"code\":\"COL\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchStatus_valid_returns200() throws Exception {
        ZoneResponse inactive = new ZoneResponse(1L, "Colombo", "COL", null, ZoneType.DISTRICT,
                ZoneStatus.INACTIVE, Instant.now(), Instant.now());
        when(zoneService.updateStatus(1L, ZoneStatus.INACTIVE)).thenReturn(inactive);

        mockMvc.perform(patch("/api/zones/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void patchStatus_invalidValue_returns400() throws Exception {
        mockMvc.perform(patch("/api/zones/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELETED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchStatus_missingStatus_returns400WithFieldError() throws Exception {
        mockMvc.perform(patch("/api/zones/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }
}
