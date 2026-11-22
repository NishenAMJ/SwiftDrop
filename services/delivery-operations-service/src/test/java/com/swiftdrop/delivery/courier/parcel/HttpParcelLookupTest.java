package com.swiftdrop.delivery.courier.parcel;

import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class HttpParcelLookupTest {

    private MockRestServiceServer server;
    private HttpParcelLookup lookup;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("http://parcel-service");
        server = MockRestServiceServer.bindTo(builder).build();
        lookup = new HttpParcelLookup(builder.build());
    }

    @Test
    void readsTheParcelIdAndForwardsTheCallersToken() {
        var id = UUID.randomUUID();
        var customer = UUID.randomUUID();
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000125"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer dispatcher-token"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"trackingNumber\":\"SWD-2026-000125\","
                        + "\"status\":\"CONFIRMED\",\"customerId\":\"" + customer + "\"}",
                        MediaType.APPLICATION_JSON));

        var parcel = lookup.find("SWD-2026-000125", "dispatcher-token");

        assertThat(parcel.parcelId()).isEqualTo(id);
        assertThat(parcel.trackingNumber()).isEqualTo("SWD-2026-000125");
        assertThat(parcel.customerId()).isEqualTo(customer);
        server.verify();
    }

    @Test
    void readsTheCashOnDeliveryDetailsOfAMerchantOrder() {
        var id = UUID.randomUUID();
        var merchant = UUID.randomUUID();
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000127"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"trackingNumber\":\"SWD-2026-000127\","
                        + "\"customerId\":null,\"merchantId\":\"" + merchant + "\",\"paymentMethod\":\"COD\","
                        + "\"codAmount\":2500.00,\"pricingSnapshotId\":\"SNP-ABC\",\"currency\":\"LKR\"}",
                        MediaType.APPLICATION_JSON));

        var parcel = lookup.find("SWD-2026-000127", "token");

        assertThat(parcel.merchantId()).isEqualTo(merchant);
        assertThat(parcel.codAmount()).isEqualByComparingTo("2500.00");
        assertThat(parcel.pricingSnapshotId()).isEqualTo("SNP-ABC");
        assertThat(parcel.collectsCash()).isTrue();
    }

    @Test
    void readsWhoToMeetAndWhereAtBothEnds() {
        var id = UUID.randomUUID();
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000128"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"trackingNumber\":\"SWD-2026-000128\","
                        + "\"pickupAddress\":{\"contactName\":\"Nimal\",\"contactPhone\":\"0771234567\","
                        + "\"streetAddress\":\"12 Main St\",\"city\":\"Colombo\",\"postalCode\":\"\"},"
                        + "\"deliveryAddress\":{\"contactName\":\"Kamala\",\"contactPhone\":\"0777654321\","
                        + "\"streetAddress\":\"45 Lake Rd\",\"city\":\"Kandy\",\"postalCode\":\"20000\"}}",
                        MediaType.APPLICATION_JSON));

        var parcel = lookup.find("SWD-2026-000128", "token");

        assertThat(parcel.pickup()).isEqualTo(new ParcelContact("Nimal", "0771234567", "12 Main St, Colombo"));
        assertThat(parcel.delivery()).isEqualTo(new ParcelContact("Kamala", "0777654321", "45 Lake Rd, Kandy, 20000"));
    }

    @Test
    void readsTheBookingStatusAndWeight() {
        var id = UUID.randomUUID();
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000129"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"trackingNumber\":\"SWD-2026-000129\","
                        + "\"status\":\"CANCELLED\",\"weightKg\":12.50}", MediaType.APPLICATION_JSON));

        var parcel = lookup.find("SWD-2026-000129", "token");

        assertThat(parcel.isCancelled()).isTrue();
        assertThat(parcel.weightKg()).isEqualByComparingTo("12.50");
    }

    @Test
    void aBookingWithoutACustomerStillResolves() {
        var id = UUID.randomUUID();
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000126"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"trackingNumber\":\"SWD-2026-000126\","
                        + "\"customerId\":null}", MediaType.APPLICATION_JSON));

        var parcel = lookup.find("SWD-2026-000126", "token");

        assertThat(parcel.parcelId()).isEqualTo(id);
        assertThat(parcel.customerId()).isNull();
    }

    @Test
    void anUnknownTrackingNumberIsNotFound() {
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-999999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> lookup.find("SWD-2026-999999", "token"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SWD-2026-999999");
    }

    @Test
    void aFailingParcelServiceIsReportedAsUnavailable() {
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000125"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> lookup.find("SWD-2026-000125", "token"))
                .isInstanceOf(ParcelServiceUnavailableException.class);
    }

    @Test
    void aRejectedTokenIsAlsoReportedAsUnavailableNotAsNotFound() {
        server.expect(requestTo("http://parcel-service/api/v1/dispatch/parcels/track/SWD-2026-000125"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> lookup.find("SWD-2026-000125", "token"))
                .isInstanceOf(ParcelServiceUnavailableException.class);
    }
}
