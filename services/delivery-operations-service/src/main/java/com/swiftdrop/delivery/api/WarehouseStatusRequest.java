package com.swiftdrop.delivery.api;

import com.swiftdrop.delivery.domain.WarehouseStatus;
import jakarta.validation.constraints.NotNull;

public record WarehouseStatusRequest(@NotNull WarehouseStatus status) {
}
