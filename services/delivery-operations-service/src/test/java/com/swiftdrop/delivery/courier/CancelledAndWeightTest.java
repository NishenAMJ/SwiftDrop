package com.swiftdrop.delivery.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.CourierResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.events.OutboxEventRepository;
import com.swiftdrop.delivery.courier.parcel.ParcelLookup;
import com.swiftdrop.delivery.courier.parcel.ParcelRef;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import com.swiftdrop.delivery.exception.AssignmentRuleException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A cancelled parcel is never scheduled, a category's weight limit holds, and a late cancellation stops the job. */
@SpringBootTest
@AutoConfigureMockMvc
class CancelledAndWeightTest {

    @Autowired private MockMvc mvc;
    @Autowired private CourierCategoryService categoryService;
    @Autowired private CourierService courierService;
    @Autowired private DeliveryAssignmentService assignmentService;
    @Autowired private OutboxEventRepository outbox;
    @MockitoBean private ParcelLookup parcelLookup;

    private ParcelRef parcel(String tracking, String status, String weightKg) {
        return new ParcelRef(UUID.randomUUID(), tracking, UUID.randomUUID(), null, null, null, null, null, null,
                status, weightKg == null ? null : new BigDecimal(weightKg));
    }

    private CourierResponse courier(String code, BigDecimal limit) {
        var category = categoryService.create(new CreateCourierCategoryRequest(code, "Category " + code, null, limit));
        return courierService.create(new CreateCourierRequest(UUID.randomUUID(), "Courier " + code,
                "+94 77 123 4567", null, category.id()));
    }

    private AssignParcelRequest request(String tracking, UUID courierId) {
        return new AssignParcelRequest(tracking, courierId, "Dispatcher", null);
    }

    @Test
    void aCancelledParcelIsNeverAssigned() {
        var tracking = "SWD-2026-000801";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(parcel(tracking, "CANCELLED", "2"));
        var courier = courier("CXL-A", new BigDecimal("50.00"));

        assertThatThrownBy(() -> assignmentService.assign(request(tracking, courier.id()), "token"))
                .isInstanceOfSatisfying(AssignmentRuleException.class,
                        e -> assertThat(e.getCode()).isEqualTo(AssignmentRuleException.PARCEL_CANCELLED));

        assertThat(assignmentService.list(tracking, null, null, org.springframework.data.domain.Pageable.unpaged()))
                .isEmpty();
    }

    @Test
    void theApiExplainsARefusalWithACodeTheScreenCanUse() throws Exception {
        var tracking = "SWD-2026-000802";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(parcel(tracking, "CANCELLED", "2"));
        var courier = courier("CXL-B", new BigDecimal("50.00"));

        mvc.perform(post("/api/v1/assignments")
                        .with(jwt().jwt(t -> t.subject(UUID.randomUUID().toString()).claim("roles", List.of("DISPATCHER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelReference\":\"" + tracking + "\",\"courierId\":\"" + courier.id()
                                + "\",\"assignedBy\":\"Dispatcher\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("PARCEL_CANCELLED"));
    }

    @Test
    void aParcelOverTheCategoryLimitIsRefusedAndOneAtTheLimitIsNot() {
        var courier = courier("WGT-A", new BigDecimal("50.00"));
        var heavy = "SWD-2026-000811";
        var exact = "SWD-2026-000812";
        when(parcelLookup.find(eq(heavy), any())).thenReturn(parcel(heavy, "BOOKED", "50.01"));
        when(parcelLookup.find(eq(exact), any())).thenReturn(parcel(exact, "BOOKED", "50.00"));

        assertThatThrownBy(() -> assignmentService.assign(request(heavy, courier.id()), "token"))
                .isInstanceOfSatisfying(AssignmentRuleException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(AssignmentRuleException.PARCEL_TOO_HEAVY);
                    assertThat(e.getMessage()).contains("50.01").contains("50");
                });
        assertThat(assignmentService.assign(request(exact, courier.id()), "token").status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void noLimitOrNoKnownWeightMeansNoCheck() {
        var unlimited = courier("WGT-B", null);
        var limited = courier("WGT-C", new BigDecimal("10.00"));
        var big = "SWD-2026-000821";
        var unknown = "SWD-2026-000822";
        when(parcelLookup.find(eq(big), any())).thenReturn(parcel(big, "BOOKED", "500"));
        when(parcelLookup.find(eq(unknown), any())).thenReturn(parcel(unknown, "BOOKED", null));

        assertThat(assignmentService.assign(request(big, unlimited.id()), "token").status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignmentService.assign(request(unknown, limited.id()), "token").status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Test
    void aLateCancellationStopsTheOpenAssignmentOnceAndPublishesNothing() {
        var tracking = "SWD-2026-000831";
        var ref = parcel(tracking, "BOOKED", "2");
        when(parcelLookup.find(eq(tracking), any())).thenReturn(ref);
        var assignment = assignmentService.assign(request(tracking, courier("CXL-C", new BigDecimal("50.00")).id()),
                "token");
        assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(AssignmentStatus.PICKED_UP, null));
        var eventsBefore = outbox.count();

        assertThat(assignmentService.cancelForParcel(ref.parcelId())).isEqualTo(1);
        assertThat(assignmentService.cancelForParcel(ref.parcelId())).isZero();

        assertThat(assignmentService.get(assignment.id()).status()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(assignmentService.get(assignment.id()).completedAt()).isNotNull();
        var history = assignmentService.history(assignment.id());
        var last = history.get(history.size() - 1);
        assertThat(last.fromStatus()).isEqualTo(AssignmentStatus.PICKED_UP);
        assertThat(last.toStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(last.changedByRole()).isEqualTo("SYSTEM");
        assertThat(outbox.count()).isEqualTo(eventsBefore);
    }

    @Test
    void aFinishedDeliveryIsNotTouchedByACancellation() {
        var tracking = "SWD-2026-000841";
        var ref = parcel(tracking, "BOOKED", "2");
        when(parcelLookup.find(eq(tracking), any())).thenReturn(ref);
        var assignment = assignmentService.assign(request(tracking, courier("CXL-D", new BigDecimal("50.00")).id()),
                "token");
        for (var next : List.of(AssignmentStatus.PICKED_UP, AssignmentStatus.OUT_FOR_DELIVERY)) {
            assignmentService.updateStatus(assignment.id(), new UpdateAssignmentStatusRequest(next, null));
        }
        assignmentService.recordProof(assignment.id(), new RecordProofRequest("Kamala", null, null, null, null));

        assertThat(assignmentService.cancelForParcel(ref.parcelId())).isZero();
        assertThat(assignmentService.get(assignment.id()).status()).isEqualTo(AssignmentStatus.DELIVERED);
    }

    @Test
    void aPersonCannotSetCancelledThemselves() {
        var tracking = "SWD-2026-000851";
        when(parcelLookup.find(eq(tracking), any())).thenReturn(parcel(tracking, "BOOKED", "2"));
        var assignment = assignmentService.assign(request(tracking, courier("CXL-E", new BigDecimal("50.00")).id()),
                "token");

        assertThatThrownBy(() -> assignmentService.updateStatus(assignment.id(),
                new UpdateAssignmentStatusRequest(AssignmentStatus.CANCELLED, null)))
                .isInstanceOf(InvalidOperationException.class);
    }
}
