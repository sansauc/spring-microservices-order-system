package com.sansa.service_order.service;

import com.sansa.service_order.client.CustomerClient;
import com.sansa.service_order.dto.CustomerResponse;
import com.sansa.service_order.utils.exceptions.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerService {

    private final CustomerClient customerClient;

    @Retry(name = "service-customer", fallbackMethod = "fallbackFindCustomerById")
    @CircuitBreaker(name="service-customer")
    @Bulkhead(name = "service-customer")
    public Optional<CustomerResponse> findCustomerById(Long id){

        return customerClient.findCustomerById(id);

    }

    private Optional<CustomerResponse> fallbackFindCustomerById(Long customerId, Throwable exception) {
        log.warn("Fallback triggered for customer {}. Cause: {}", customerId, exception.getMessage());

        throw new ServiceUnavailableException("Customer service is currently unavailable", exception);

    }

    // 4xx errors from the remote service: not an outage, rethrown as is
    private Optional<CustomerResponse> fallbackFindCustomerById(Long customerId, FeignException.FeignClientException exception) {
        throw exception;
    }

    /** The fallback goes on @Retry (the outermost aspect) and not on @CircuitBreaker: if it were on the breaker,
     it would turn the exception into ServiceUnavailableException before Retry evaluates it, and
     would retry even with the breaker open or on 4xx errors. This way Retry sees the original exception
     and the fallback runs only once, when retries are exhausted.**/

}
