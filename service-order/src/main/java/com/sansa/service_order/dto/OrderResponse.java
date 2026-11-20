package com.sansa.service_order.dto;

import com.sansa.service_order.model.Status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OrderResponse(Long id,
                            Long customerId,
                            LocalDate date,
                            Status status,
                            List<OrderItemResponse> items,
                            BigDecimal total) {
}
