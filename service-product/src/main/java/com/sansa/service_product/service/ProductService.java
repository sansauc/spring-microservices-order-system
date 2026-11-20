package com.sansa.service_product.service;

import com.sansa.service_product.model.Product;

import java.util.List;

public interface ProductService {

    List<Product> findAll();

    Product findById(Long id);

    void save(Product product);

    void decreaseStock(Long id, int quantity, String idempotencyKey);

    void increaseStock(Long id, int quantity, String idempotencyKey);
}
