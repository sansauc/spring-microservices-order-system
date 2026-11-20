package com.sansa.service_order.service;

import com.sansa.service_order.client.ProductClient;
import com.sansa.service_order.dto.ProductResponse;
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
public class ProductService {

    private final ProductClient productClient;

    @Retry(name="service-product", fallbackMethod = "fallbackFindProductById")
    @CircuitBreaker(name="service-product")
    @Bulkhead(name = "service-product")
    public Optional<ProductResponse> findProductById(Long id){
        return productClient.findProductById(id);
    }

    @Retry(name = "service-product-write", fallbackMethod = "fallbackDecreaseStock")
    @CircuitBreaker(name="service-product")
    @Bulkhead(name = "service-product-write")
    public void decreaseStock(Long id, int quantity, String key){
        productClient.decreaseStock(id, quantity, key);
    }

    // Anything else (RetryableException on timeout, 5xx, CallNotPermittedException, BulkheadFullException...)
    private Optional<ProductResponse> fallbackFindProductById(Long productId, Throwable exception){
        log.warn("Fallback FindProductByID triggered for product {}. Cause: {}", productId, exception.getMessage());
        throw new ServiceUnavailableException("Product service unavailable");
    }

    // Handles FeignClientException (any 4xx: 404, 409...): business errors of the remote service (e.g. 409 insufficient stock). It exists so business errors are not disguised as an outage.
    private Optional<ProductResponse> fallbackFindProductById(Long productId, FeignException.FeignClientException exception){
        throw exception;
    }

    private void fallbackDecreaseStock(Long productId, int quantity, String key, FeignException.FeignClientException exception){
        throw exception;
    }

    private void fallbackDecreaseStock(Long productId, int quantity, String key, Throwable exception){
        log.warn("Fallback fallbackDecreaseStock triggered for product {}. Cause: {}", productId, exception.getMessage());
        throw new ServiceUnavailableException("Could not decrease stock for product " + productId);
    }

    /** The fallback goes on @Retry (the outermost aspect) and not on @CircuitBreaker: if it were on the breaker, it would turn the exception into ServiceUnavailableException before Retry evaluates it, and
 would retry even with the breaker open or on 4xx errors. This way Retry sees the original exception
 and the fallback runs only once, when retries are exhausted.**/

    @Retry(name = "service-product-write", fallbackMethod = "fallbackIncreaseStock")
    @CircuitBreaker(name="service-product")
    @Bulkhead(name = "service-product-write")
    public void increaseStock(Long id, int quantity, String key){
        productClient.increaseStock(id, quantity, key);
    }

    private void fallbackIncreaseStock(Long productId, int quantity, String key, FeignException.FeignClientException exception){
        throw exception;
    }

    private void fallbackIncreaseStock(Long productId, int quantity, String key, Throwable exception){
        log.warn("Fallback fallbackIncreaseStock triggered for product {}. Cause: {}", productId, exception.getMessage());
        throw new ServiceUnavailableException("Could not restore stock for product " + productId);
    }
}
