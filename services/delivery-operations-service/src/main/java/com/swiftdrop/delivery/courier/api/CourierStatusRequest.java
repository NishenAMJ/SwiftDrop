package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.CourierStatus;
import jakarta.validation.constraints.NotNull;

public record CourierStatusRequest(@NotNull CourierStatus status) {
}
