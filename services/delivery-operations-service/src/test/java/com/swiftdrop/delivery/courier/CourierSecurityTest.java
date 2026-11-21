package com.swiftdrop.delivery.courier;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.AssignmentResponse;
import com.swiftdrop.delivery.courier.api.CourierResponse;
import com.swiftdrop.delivery.courier.api.CreateCourierCategoryRequest;
import com.swiftdrop.delivery.courier.api.CreateCourierRequest;
import com.swiftdrop.delivery.courier.service.CourierCategoryService;
import com.swiftdrop.delivery.courier.service.CourierService;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourierSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CourierCategoryService categoryService;
    @Autowired
    private CourierService courierService;
    @Autowired
    private DeliveryAssignmentService assignmentService;

    private final UUID dispatcherUser = UUID.randomUUID();
    private final UUID aliceUser = UUID.randomUUID();
    private final UUID bobUser = UUID.randomUUID();
    private CourierResponse alice;
    private CourierResponse bob;
    private AssignmentResponse aliceParcel;
    private AssignmentResponse bobParcel;

    @BeforeEach
    void setUp() {
        var suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var category = categoryService.create(new CreateCourierCategoryRequest("SEC-" + suffix, "Security " + suffix,
                null, new BigDecimal("30.00")));
        alice = courierService.create(new CreateCourierRequest(aliceUser, "Alice", "+94 77 000 0001", null, category.id()));
        bob = courierService.create(new CreateCourierRequest(bobUser, "Bob", "+94 77 000 0002", null, category.id()));
        // Each test run uses fresh tracking numbers so assignments never collide.
        var base = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000) + 100_000;
        aliceParcel = assignmentService.assign(
                new AssignParcelRequest("SWD-2026-" + base, alice.id(), "Dispatcher", null), "token");
        bobParcel = assignmentService.assign(
                new AssignParcelRequest("SWD-2026-" + (base + 1), bob.id(), "Dispatcher", null), "token");
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/courier-categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/assignments")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckStaysPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void onlyDispatchersManageCategoriesAndCouriers() throws Exception {
        var body = "{\"code\":\"NEWCAT\",\"name\":\"New category\"}";
        mvc.perform(post("/api/v1/courier-categories").with(courier(aliceUser))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/courier-profiles").with(courier(aliceUser))).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/courier-categories").with(courier(aliceUser))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/courier-profiles").with(dispatcher())).andExpect(status().isOk());
    }

    @Test
    void customersAndMerchantsHaveNoAccess() throws Exception {
        var customer = jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        mvc.perform(get("/api/v1/assignments").with(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/courier-categories").with(customer)).andExpect(status().isForbidden());
    }

    @Test
    void warehouseOperationsAreForDispatchersOnly() throws Exception {
        mvc.perform(get("/api/v1/warehouses").with(courier(aliceUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/warehouses").with(dispatcher())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/transfers").with(courier(aliceUser))).andExpect(status().isForbidden());
    }

    @Test
    void aCourierSeesOnlyTheirOwnAssignments() throws Exception {
        mvc.perform(get("/api/v1/assignments").with(courier(aliceUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + aliceParcel.id() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + bobParcel.id() + "')]").doesNotExist());
    }

    @Test
    void aCourierCannotAskForAnotherCouriersAssignments() throws Exception {
        mvc.perform(get("/api/v1/assignments").param("courierId", bob.id().toString()).with(courier(aliceUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assignments/" + bobParcel.id()).with(courier(aliceUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aParcelReferenceFilterDoesNotLeakAnotherCouriersAssignment() throws Exception {
        mvc.perform(get("/api/v1/assignments").param("parcelReference", bobParcel.parcelReference())
                        .with(courier(aliceUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void aCourierCanOnlyMoveTheirOwnParcelAlong() throws Exception {
        var pickedUp = "{\"status\":\"PICKED_UP\"}";
        mvc.perform(patch("/api/v1/assignments/" + bobParcel.id() + "/status").with(courier(aliceUser))
                .contentType(MediaType.APPLICATION_JSON).content(pickedUp)).andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/assignments/" + aliceParcel.id() + "/status").with(courier(aliceUser))
                        .contentType(MediaType.APPLICATION_JSON).content(pickedUp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));
    }

    @Test
    void aDispatcherCannotMarkParcelsAsDeliveredOrMoveThemAlong() throws Exception {
        mvc.perform(patch("/api/v1/assignments/" + aliceParcel.id() + "/status").with(dispatcher())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/assignments/" + aliceParcel.id() + "/proof").with(dispatcher())
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\"Someone\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCourierWithoutADeliveryProfileIsRefused() throws Exception {
        mvc.perform(get("/api/v1/assignments").with(courier(UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    @Test
    void aCourierCanLookUpOnlyTheirOwnProfile() throws Exception {
        mvc.perform(get("/api/v1/courier-profiles/by-identity/" + aliceUser).with(courier(aliceUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Alice"));
        mvc.perform(get("/api/v1/courier-profiles/by-identity/" + bobUser).with(courier(aliceUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/courier-profiles/by-identity/" + bobUser).with(dispatcher()))
                .andExpect(status().isOk());
    }

    @Test
    void dispatchersAssignParcelsAndCouriersCannot() throws Exception {
        var body = "{\"parcelReference\":\"SWD-2026-777001\",\"courierId\":\"" + alice.id()
                + "\",\"assignedBy\":\"Dispatcher\"}";
        mvc.perform(post("/api/v1/assignments").with(courier(aliceUser))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/assignments").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelReference").value("SWD-2026-777001"));
    }

    @Test
    void aCourierReportsIssuesOnlyAsThemselvesAndOnlyDispatchersReviewThem() throws Exception {
        var asBob = "{\"courierId\":\"" + bob.id() + "\",\"issueType\":\"OTHER\",\"description\":\"Flat tyre\"}";
        mvc.perform(post("/api/v1/delivery-issues").with(courier(aliceUser))
                .contentType(MediaType.APPLICATION_JSON).content(asBob)).andExpect(status().isForbidden());

        var asAlice = "{\"courierId\":\"" + alice.id() + "\",\"issueType\":\"OTHER\",\"description\":\"Flat tyre\"}";
        var created = mvc.perform(post("/api/v1/delivery-issues").with(courier(aliceUser))
                        .contentType(MediaType.APPLICATION_JSON).content(asAlice))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var issueId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        var review = "{\"status\":\"RESOLVED\",\"reviewedBy\":\"Dispatcher\"}";
        mvc.perform(patch("/api/v1/delivery-issues/" + issueId + "/review").with(courier(aliceUser))
                .contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/delivery-issues/" + issueId + "/review").with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/delivery-issues/" + issueId).with(courier(bobUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/delivery-issues/" + issueId).with(courier(aliceUser)))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor courier(UUID identityUserId) {
        return jwt().jwt(token -> token.subject(identityUserId.toString()).claim("roles", List.of("COURIER")))
                .authorities(new SimpleGrantedAuthority("ROLE_COURIER"));
    }

    private RequestPostProcessor dispatcher() {
        return jwt().jwt(token -> token.subject(dispatcherUser.toString()).claim("roles", List.of("DISPATCHER")))
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }
}
