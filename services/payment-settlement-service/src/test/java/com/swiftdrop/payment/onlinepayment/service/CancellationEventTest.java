package com.swiftdrop.payment.onlinepayment.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class CancellationEventTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private String body() { return """
        {"eventId":"00000000-0000-0000-0000-000000000001","eventType":"parcel.cancelled",
        "version":1,"producer":"parcel-pricing-service","occurredAt":"2026-11-22T00:00:00Z",
        "data":{"parcelId":"00000000-0000-0000-0000-000000000002",
        "customerUserId":"00000000-0000-0000-0000-000000000003","paymentMethod":"COD",
        "deliveryStatus":"CANCELLED","cancelledAt":"2026-11-22T00:00:00Z",
        "cancellationFee":0,"refundAmount":0}}
        """; }
    @Test void acceptsCodWithoutPaymentId() { assertNull(CancellationEvent.parse(body(),json).paymentId()); }
    @Test void rejectsOnlineWithoutPaymentId() { assertThrows(IllegalArgumentException.class,()->CancellationEvent.parse(body().replace("COD","ONLINE"),json)); }
    @Test void rejectsMalformedEnvelopeAndDates() {
        for(String input:new String[]{"null","{}","{",body().replace("2026-11-22T00:00:00Z","bad"),body().replace("\"version\":1","\"version\":1.5")})
            assertThrows(IllegalArgumentException.class,()->CancellationEvent.parse(input,json));
    }
    @Test void rejectsCodMoneyAndOversizedAmounts() {
        for(String amount:new String[]{"1","-1","0.001","1e1000"})
            assertThrows(IllegalArgumentException.class,()->CancellationEvent.parse(body().replace("\"refundAmount\":0","\"refundAmount\":"+amount),json));
    }
}
