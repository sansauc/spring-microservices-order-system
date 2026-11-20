package com.sansa.service_order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull(message = "productId is required") @Positive(message = "productId must be positive") Long productId,
        @Positive(message = "quantity must be greater than 0") int quantity) {

}
