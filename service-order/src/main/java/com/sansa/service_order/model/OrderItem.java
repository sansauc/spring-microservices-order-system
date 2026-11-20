package com.sansa.service_order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {

    private Long id;
    private Long productId;
    private int quantity;
    private BigDecimal price;

}
