package com.sansa.service_order.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Order {

    private Long id;
    private Long customerId;
    private LocalDate date;
    private Status status;
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
    private BigDecimal total;

}
