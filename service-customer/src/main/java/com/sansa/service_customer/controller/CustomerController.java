package com.sansa.service_customer.controller;

import com.sansa.service_customer.model.Customer;
import com.sansa.service_customer.service.CustomerService;
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
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public ResponseEntity<List<Customer>> findAllCustomers() {
        log.info("Request to find all customers");
       return ResponseEntity.ok(customerService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> findCustomerById(@PathVariable Long id){
        log.info("Request to get product by id: {}", id);
        return ResponseEntity.ok(customerService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Void> createCustomer(@Valid @RequestBody Customer customer){
        log.info("Request to create customer: {}", customer);
        customerService.save(customer);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
