package com.sansa.service_order.client;

import com.sansa.service_order.dto.CustomerResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

@FeignClient(name = "service-customer")
public interface CustomerClient {

    @GetMapping("/api/v1/customers/{id}")
    Optional<CustomerResponse> findCustomerById(@PathVariable("id") Long id);


}
