package com.sansa.service_product.controller;

import com.sansa.service_product.model.Product;
import com.sansa.service_product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
// No class-level @Validated on purpose: with it, a @Positive on a @RequestParam throws ConstraintViolationException,
// which the GlobalExceptionHandler does not handle (it would return 500). Without it, Spring uses its native validation and throws
// HandlerMethodValidationException, which the handler does translate to 400. @Valid on the body works the same.
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<Product>> findAllProducts(){
        log.info("Request to find all products");
        return ResponseEntity.ok(productService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> findProductById(@PathVariable Long id){
        log.info("Request to find product with id: {}", id);
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Void> createProduct(@Valid @RequestBody Product product){
        log.info("Request to create product: {}", product);
        productService.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<Void> decreaseStock(@PathVariable Long id, @RequestParam @Positive(message = "quantity must be greater than 0") int quantity, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("Request to decrease stock of product with id: {} by {}", id, quantity);
        productService.decreaseStock(id, quantity, idempotencyKey);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/stock/restore")
    public ResponseEntity<Void> increaseStock(@PathVariable Long id,
                                              @RequestParam @Positive(message = "quantity must be greater than 0") int quantity,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        log.info("Request to restore stock of product with id: {} by {}", id, quantity);
        productService.increaseStock(id, quantity, idempotencyKey);
        return ResponseEntity.noContent().build();
    }
}