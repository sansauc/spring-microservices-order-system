package com.sansa.service_order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "ID is required") @Positive(message = "ID must be positive") Long id,
        @NotNull(message = "customerId is required") @Positive(message = "customerId must be positive") Long customerId,
        @NotEmpty(message = "items must contain at least one item") List<@NotNull @Valid OrderItemRequest> items) {

}
