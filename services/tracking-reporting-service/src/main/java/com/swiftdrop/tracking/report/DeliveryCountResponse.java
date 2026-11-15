package com.swiftdrop.tracking.report;

import java.time.LocalDate;

public record DeliveryCountResponse(LocalDate period, long deliveredParcels) {
}
