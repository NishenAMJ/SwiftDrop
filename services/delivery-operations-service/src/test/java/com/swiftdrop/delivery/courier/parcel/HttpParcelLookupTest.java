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
        server.expect(requestTo("http://parcel-service/api/v1/bookings/track/SWD-2026-000125"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer dispatcher-token"))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"trackingNumber\":\"SWD-2026-000125\","
                        + "\"status\":\"CONFIRMED\",\"customerId\":\"" + UUID.randomUUID() + "\"}",
                        MediaType.APPLICATION_JSON));

        var parcel = lookup.find("SWD-2026-000125", "dispatcher-token");

        assertThat(parcel.parcelId()).isEqualTo(id);
        assertThat(parcel.trackingNumber()).isEqualTo("SWD-2026-000125");
        server.verify();
    }

    @Test
    void anUnknownTrackingNumberIsNotFound() {
        server.expect(requestTo("http://parcel-service/api/v1/bookings/track/SWD-2026-999999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> lookup.find("SWD-2026-999999", "token"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SWD-2026-999999");
    }

    @Test
    void aFailingParcelServiceIsReportedAsUnavailable() {
        server.expect(requestTo("http://parcel-service/api/v1/bookings/track/SWD-2026-000125"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> lookup.find("SWD-2026-000125", "token"))
                .isInstanceOf(ParcelServiceUnavailableException.class);
    }

    @Test
    void aRejectedTokenIsAlsoReportedAsUnavailableNotAsNotFound() {
        server.expect(requestTo("http://parcel-service/api/v1/bookings/track/SWD-2026-000125"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> lookup.find("SWD-2026-000125", "token"))
                .isInstanceOf(ParcelServiceUnavailableException.class);
    }
}
