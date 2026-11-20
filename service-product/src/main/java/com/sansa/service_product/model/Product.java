package com.sansa.service_product.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @NotNull(message = "ID is required")
    @Positive(message = "ID must be positive")
    private Long id;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @NotNull(message = "price is required")
    @Positive(message = "price must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "price must have at most 10 integer and 2 decimal digits")
    private BigDecimal price;

    @PositiveOrZero(message = "stock must not be negative")
    private int stock;

}
