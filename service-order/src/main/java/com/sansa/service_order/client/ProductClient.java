package com.sansa.service_order.client;

import com.sansa.service_order.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@FeignClient(name = "service-product")
public interface ProductClient {

    @GetMapping("/api/v1/products/{id}")
    Optional<ProductResponse> findProductById(@PathVariable("id") Long id);

    @PutMapping("/api/v1/products/{id}/stock")
    void decreaseStock(@PathVariable("id") Long id,
                       @RequestParam("quantity") int quantity,
                       @RequestHeader("Idempotency-Key") String idempotencyKey);

    @PutMapping("/api/v1/products/{id}/stock/restore")
    void increaseStock(@PathVariable("id") Long id,
                       @RequestParam("quantity") int quantity,
                       @RequestHeader("Idempotency-Key") String idempotencyKey);
}
