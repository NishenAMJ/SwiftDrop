package com.swiftdrop.identity.courier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.ApiTestSupport;
import com.swiftdrop.identity.event.OutboxEvent;
import com.swiftdrop.identity.event.OutboxEventRepository;

class CourierFlowTests extends ApiTestSupport {

    private static final String MOTORCYCLE = """
        {"nationalIdNumber": "199012345678", "vehicleType": "MOTORCYCLE",
         "drivingLicenceNumber": "b1234567", "vehicleRegistrationNumber": "wp-abc-1234"}
        """;

    @Autowired
    private OutboxEventRepository outbox;

    @Test
    void approvalMakesTheUserACourier() throws Exception {
        Session applicant = registerAndLogin("COURIER");
        String dispatcher = activeDispatcherToken();

        apply(applicant.accessToken(), MOTORCYCLE)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(applicant.userId()))
            .andExpect(jsonPath("$.fullName").value("Test User"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.drivingLicenceNumber").value("B1234567"));

        getWithToken("/api/v1/couriers?status=PENDING&vehicleType=MOTORCYCLE&size=100", dispatcher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].userId", hasItem(applicant.userId())))
            .andExpect(jsonPath("$.content[*].vehicleType", everyItem(is("MOTORCYCLE"))));

        decide(dispatcher, applicant.userId(), "approve", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.lastDecision.decidedBy").exists());

        Session courier = session(refresh(applicant.refreshToken()).andReturn());
        assertThat((List<String>) claims(courier.accessToken()).read("$.roles")).containsExactly("COURIER");
        getWithToken("/api/v1/couriers/me", courier.accessToken())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.lastDecision.decidedBy").doesNotExist());

        DocumentContext approved = outbox.findAll().stream()
            .filter(e -> e.getEventType().equals("courier.approved"))
            .map(OutboxEvent::getPayload).map(JsonPath::parse)
            .filter(json -> applicant.userId().equals(json.read("$.data.userId")))
            .findFirst().orElseThrow();
        assertThat((String) approved.read("$.data.vehicleType")).isEqualTo("MOTORCYCLE");
        assertThat((String) approved.read("$.data.fullName")).isEqualTo("Test User");
        assertThat((String) approved.read("$.data.previousStatus")).isEqualTo("PENDING");

        decide(dispatcher, applicant.userId(), "suspend", "{\"reason\": \"Repeated late deliveries\"}")
            .andExpect(jsonPath("$.status").value("SUSPENDED"));
        Session suspended = session(refresh(courier.refreshToken()).andReturn());
        assertThat((List<String>) claims(suspended.accessToken()).read("$.roles")).isEmpty();
    }

    @Test
    void bicyclesNeedNoDocumentsButOtherVehiclesDo() throws Exception {
        apply(registerAndLogin("COURIER").accessToken(), """
            {"nationalIdNumber": "200012345678", "vehicleType": "BICYCLE", "drivingLicenceNumber": "IGNORED"}
            """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.drivingLicenceNumber").doesNotExist());

        apply(registerAndLogin("COURIER").accessToken(), """
            {"nationalIdNumber": "200012345678", "vehicleType": "CAR"}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("vehicleDocuments"));
    }

    @Test
    void rejectedCourierCanResubmit() throws Exception {
        Session applicant = registerAndLogin("COURIER");
        String dispatcher = activeDispatcherToken();
        apply(applicant.accessToken(), MOTORCYCLE).andExpect(status().isCreated());
        apply(applicant.accessToken(), MOTORCYCLE).andExpect(status().isConflict());

        decide(dispatcher, applicant.userId(), "reject", "{\"reason\": \"Licence photo unreadable\"}")
            .andExpect(jsonPath("$.status").value("REJECTED"));

        apply(applicant.accessToken(), MOTORCYCLE.replace("MOTORCYCLE", "THREE_WHEELER"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.vehicleType").value("THREE_WHEELER"))
            .andExpect(jsonPath("$.lastDecision.status").value("REJECTED"));
    }

    @Test
    void permissions() throws Exception {
        Session applicant = registerAndLogin("COURIER");
        String dispatcher = activeDispatcherToken();
        apply(applicant.accessToken(), MOTORCYCLE).andExpect(status().isCreated());

        apply(dispatcher, MOTORCYCLE).andExpect(status().isForbidden());
        decide(applicant.accessToken(), applicant.userId(), "approve", null).andExpect(status().isForbidden());
        getWithToken("/api/v1/couriers", applicant.accessToken()).andExpect(status().isForbidden());
        getWithToken("/api/v1/couriers/" + applicant.userId(), dispatcher).andExpect(status().isOk());
        getWithToken("/api/v1/couriers/me", registerAndLogin("COURIER").accessToken()).andExpect(status().isNotFound());
    }

    private ResultActions apply(String token, String json) throws Exception {
        return postWithToken("/api/v1/couriers", token, json);
    }

    private ResultActions decide(String token, String userId, String action, String json) throws Exception {
        return postWithToken("/api/v1/couriers/" + userId + "/" + action, token, json);
    }
}
