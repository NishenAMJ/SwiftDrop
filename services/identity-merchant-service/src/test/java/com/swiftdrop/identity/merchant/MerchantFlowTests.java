package com.swiftdrop.identity.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.ApiTestSupport;
import com.swiftdrop.identity.approval.ApplicationAction;
import com.swiftdrop.identity.approval.ApplicationDecision;
import com.swiftdrop.identity.approval.ApplicationDecisionRepository;
import com.swiftdrop.identity.event.OutboxEvent;
import com.swiftdrop.identity.event.OutboxEventRepository;

class MerchantFlowTests extends ApiTestSupport {

    static final String APPLICATION = """
        {
          "businessName": "Perera Electronics",
          "businessType": "SOLE_PROPRIETORSHIP",
          "contactPhone": "+94771234567",
          "contactEmail": "Shop@Example.com",
          "businessAddress": {"line1": "12 Galle Road", "city": "Colombo", "postalCode": "00300"}
        }
        """;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private ApplicationDecisionRepository decisions;

    @Test
    void fullLifecycleChangesRolesAndPublishesEvents() throws Exception {
        Session applicant = registerAndLogin();
        String dispatcher = activeDispatcherToken();

        String merchantId = JsonPath.read(apply(applicant.accessToken(), APPLICATION)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.contactEmail").value("shop@example.com"))
            .andExpect(jsonPath("$.businessAddress.city").value("Colombo"))
            .andExpect(jsonPath("$.lastDecision").doesNotExist())
            .andReturn().getResponse().getContentAsString(), "$.id");

        // Pending: still only a customer, but /users/me already links the merchant.
        Session pending = session(refresh(applicant.refreshToken()).andReturn());
        assertThat((List<String>) claims(pending.accessToken()).read("$.roles")).containsExactly("CUSTOMER");
        getWithToken("/api/v1/users/me", pending.accessToken())
            .andExpect(jsonPath("$.merchantId").value(merchantId));

        getWithToken("/api/v1/merchants?status=PENDING&q=perera&size=100", dispatcher)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id", hasItem(merchantId)));

        decide(dispatcher, merchantId, "approve", "{\"note\": \"Checked documents\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.lastDecision.status").value("APPROVED"))
            .andExpect(jsonPath("$.lastDecision.decidedBy").exists());

        Session approved = session(refresh(pending.refreshToken()).andReturn());
        DocumentContext claims = claims(approved.accessToken());
        assertThat((List<String>) claims.read("$.roles")).containsExactly("CUSTOMER", "MERCHANT");
        assertThat((String) claims.read("$.merchant_id")).isEqualTo(merchantId);

        // The merchant sees the decision, but not which dispatcher made it.
        getWithToken("/api/v1/merchants/me", approved.accessToken())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.lastDecision.status").value("APPROVED"))
            .andExpect(jsonPath("$.lastDecision.decidedBy").doesNotExist());

        decide(dispatcher, merchantId, "suspend", "{\"reason\": \"Too many failed deliveries\"}")
            .andExpect(jsonPath("$.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.lastDecision.reason").value("Too many failed deliveries"));
        Session suspended = session(refresh(approved.refreshToken()).andReturn());
        assertThat((List<String>) claims(suspended.accessToken()).read("$.roles")).containsExactly("CUSTOMER");
        assertThat((Map<String, Object>) claims(suspended.accessToken()).read("$")).doesNotContainKey("merchant_id");

        decide(dispatcher, merchantId, "reactivate", null).andExpect(jsonPath("$.status").value("APPROVED"));
        Session reactivated = session(refresh(suspended.refreshToken()).andReturn());
        assertThat((List<String>) claims(reactivated.accessToken()).read("$.roles")).contains("MERCHANT");

        assertThat(eventTypesFor(merchantId)).containsExactly("merchant.submitted", "merchant.approved",
                "merchant.suspended", "merchant.reactivated");
        DocumentContext suspendedEvent = event("merchant.suspended", merchantId);
        assertThat((String) suspendedEvent.read("$.data.previousStatus")).isEqualTo("APPROVED");
        assertThat((String) suspendedEvent.read("$.data.reason")).isEqualTo("Too many failed deliveries");
        assertThat((String) suspendedEvent.read("$.data.decidedBy")).isNotBlank();

        List<ApplicationDecision> trail = decisions.findByApplicationTypeAndApplicationIdOrderByDecidedAtAsc(
                ApplicationDecision.Type.MERCHANT, UUID.fromString(merchantId));
        assertThat(trail).extracting(ApplicationDecision::getAction).containsExactly(ApplicationAction.SUBMIT,
                ApplicationAction.APPROVE, ApplicationAction.SUSPEND, ApplicationAction.REACTIVATE);
        assertThat(trail.get(1).getNote()).isEqualTo("Checked documents");
    }

    @Test
    void rejectedApplicantCanResubmitButNotApplyTwice() throws Exception {
        Session applicant = registerAndLogin();
        String dispatcher = activeDispatcherToken();
        String merchantId = JsonPath.read(apply(applicant.accessToken(), APPLICATION).andReturn()
            .getResponse().getContentAsString(), "$.id");

        apply(applicant.accessToken(), APPLICATION)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"));

        decide(dispatcher, merchantId, "reject", "{\"reason\": \"Phone number unreachable\"}")
            .andExpect(jsonPath("$.status").value("REJECTED"));
        getWithToken("/api/v1/merchants/me", applicant.accessToken())
            .andExpect(jsonPath("$.lastDecision.reason").value("Phone number unreachable"));

        apply(applicant.accessToken(), APPLICATION.replace("Perera Electronics", "Perera Electronics Ltd"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(merchantId))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.businessName").value("Perera Electronics Ltd"));

        assertThat(eventTypesFor(merchantId))
            .containsExactly("merchant.submitted", "merchant.rejected", "merchant.submitted");
    }

    @Test
    void invalidTransitionsAndMissingReasonsAreRejected() throws Exception {
        Session applicant = registerAndLogin();
        String dispatcher = activeDispatcherToken();
        String merchantId = JsonPath.read(apply(applicant.accessToken(), APPLICATION).andReturn()
            .getResponse().getContentAsString(), "$.id");

        decide(dispatcher, merchantId, "suspend", "{\"reason\": \"Not yet approved\"}")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        decide(dispatcher, merchantId, "reject", null).andExpect(status().isBadRequest());
        decide(dispatcher, merchantId, "reject", "{\"reason\": \" \"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("reason"));
        decide(dispatcher, UUID.randomUUID().toString(), "approve", null).andExpect(status().isNotFound());

        decide(dispatcher, merchantId, "approve", null).andExpect(status().isOk());
        decide(dispatcher, merchantId, "approve", null).andExpect(status().isConflict());
    }

    @Test
    void applicationIsValidated() throws Exception {
        String customer = registerAndLogin().accessToken();

        apply(customer, APPLICATION.replace("SOLE_PROPRIETORSHIP", "COMPANY"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", hasItem("businessRegistrationNumber")));

        apply(customer, """
            {"businessName": "X", "businessType": "SHOP", "contactPhone": "077", "businessAddress": {}}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        apply(customer, """
            {"businessName": "Ok Name", "businessType": "INDIVIDUAL", "contactPhone": "077",
             "businessAddress": {"city": "Kandy"}}
            """)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", hasItem("contactPhone")))
            .andExpect(jsonPath("$.errors[*].field", hasItem("businessAddress.line1")));
    }

    @Test
    void onlyCustomersApplyAndOnlyDispatchersDecide() throws Exception {
        Session applicant = registerAndLogin();
        String dispatcher = activeDispatcherToken();
        String merchantId = JsonPath.read(apply(applicant.accessToken(), APPLICATION).andReturn()
            .getResponse().getContentAsString(), "$.id");

        apply(dispatcher, APPLICATION).andExpect(status().isForbidden());
        decide(applicant.accessToken(), merchantId, "approve", null).andExpect(status().isForbidden());
        getWithToken("/api/v1/merchants", applicant.accessToken()).andExpect(status().isForbidden());
        getWithToken("/api/v1/merchants/" + merchantId, applicant.accessToken()).andExpect(status().isForbidden());

        getWithToken("/api/v1/merchants/" + merchantId, dispatcher).andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(applicant.userId()));
        getWithToken("/api/v1/merchants/me", registerAndLogin().accessToken())
            .andExpect(status().isNotFound());
        getWithToken("/api/v1/merchants?sort=userId,asc", dispatcher).andExpect(status().isBadRequest());
    }

    @Test
    void approvalAlsoPublishesUserUpdatedWithTheNewRole() throws Exception {
        Session applicant = registerAndLogin();
        String merchantId = JsonPath.read(apply(applicant.accessToken(), APPLICATION).andReturn()
            .getResponse().getContentAsString(), "$.id");

        decide(activeDispatcherToken(), merchantId, "approve", null).andExpect(status().isOk());

        DocumentContext userUpdated = outbox.findAll().stream()
            .filter(e -> e.getEventType().equals("user.updated"))
            .map(OutboxEvent::getPayload).map(JsonPath::parse)
            .filter(json -> applicant.userId().equals(json.read("$.data.userId")))
            .reduce((a, b) -> b).orElseThrow();
        assertThat((List<String>) userUpdated.read("$.data.roles")).containsExactly("CUSTOMER", "MERCHANT");
        getWithToken("/api/v1/users/me", session(refresh(applicant.refreshToken()).andReturn()).accessToken())
            .andExpect(jsonPath("$.roles").value(contains("CUSTOMER", "MERCHANT")));
    }

    ResultActions apply(String token, String json) throws Exception {
        return postWithToken("/api/v1/merchants", token, json);
    }

    ResultActions decide(String token, String merchantId, String action, String json) throws Exception {
        return postWithToken("/api/v1/merchants/" + merchantId + "/" + action, token, json);
    }

    private List<String> eventTypesFor(String merchantId) {
        return outbox.findAll().stream()
            .filter(e -> e.getEventType().startsWith("merchant."))
            .filter(e -> merchantId.equals(JsonPath.read(e.getPayload(), "$.data.merchantId")))
            .sorted((a, b) -> a.getOccurredAt().compareTo(b.getOccurredAt()))
            .map(OutboxEvent::getEventType)
            .toList();
    }

    private DocumentContext event(String type, String merchantId) {
        return outbox.findAll().stream()
            .filter(e -> e.getEventType().equals(type))
            .map(OutboxEvent::getPayload).map(JsonPath::parse)
            .filter(json -> merchantId.equals(json.read("$.data.merchantId")))
            .findFirst().orElseThrow();
    }
}
