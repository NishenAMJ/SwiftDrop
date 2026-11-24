package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.CategoryStatus;
import jakarta.validation.constraints.NotNull;

public record CourierCategoryStatusRequest(@NotNull CategoryStatus status) {
}
