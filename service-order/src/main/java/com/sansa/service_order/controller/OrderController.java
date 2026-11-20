package com.sansa.service_order.controller;

import com.sansa.service_order.dto.CreateOrderRequest;
import com.sansa.service_order.model.Order;
import com.sansa.service_order.service.OrderService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @RateLimiter(name = "read-orders")
    @GetMapping
    public ResponseEntity<List<Order>> findAll() {
        log.info("Request to find all orders");
        return ResponseEntity.ok(orderService.findAll());
    }

    @RateLimiter(name = "read-orders")
    @GetMapping("/{id}")
    public ResponseEntity<Order> findById(@PathVariable Long id){
        log.info("Request to find order with id: {}", id);
        return ResponseEntity.ok(orderService.findById(id));
    }

    @RateLimiter(name = "read-orders")
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Order>> findOrdersByCustomerId(@PathVariable Long customerId){
        log.info("Request to find all orders the customer with ID: {}",customerId);
        return ResponseEntity.ok(orderService.findOrdersByCustomerId(customerId));
    }

    @RateLimiter(name = "create-order")
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody CreateOrderRequest order){
        log.info("Request to create order: {}", order);
        orderService.create(order);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
