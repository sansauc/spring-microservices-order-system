package com.sansa.service_product.service;

import com.sansa.service_product.model.IdempotencyStore;
import com.sansa.service_product.model.Product;
import com.sansa.service_product.repository.ProductRepository;
import com.sansa.service_product.utils.exceptions.InsufficientStockException;
import com.sansa.service_product.utils.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProductServiceImpl implements ProductService{

    private final ProductRepository productRepository;
    private final IdempotencyStore idempotencyStore;

    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Product whit id "+id+" not found"));
    }

    @Override
    public void save(Product product) {
        productRepository.save(product);
    }

    @Override
    public synchronized void decreaseStock(Long id, int quantity, String key) {

        if (idempotencyStore.alreadyProcessed(key) || idempotencyStore.alreadyRestored(key)) {
            return;   // already decreased, or already compensated: do nothing
        }

        if(quantity <= 0){
            throw new IllegalArgumentException("The quantity must be greater than 0.");
        }

        Product product = findById(id);

        if(product.getStock() < quantity){
            throw new InsufficientStockException("Insufficient stock for the product with ID: " + id);
        }

        product.setStock(product.getStock() - quantity);

        idempotencyStore.markProcessed(key);
    }

    @Override
    public synchronized void increaseStock(Long id, int quantity, String key) {

        if (idempotencyStore.alreadyRestored(key)) {
            return;   // already restored: do not restore twice
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("The quantity must be greater than 0.");
        }

        // Only restored if the decrease was actually processed
        if (idempotencyStore.alreadyProcessed(key)) {
            Product product = findById(id);
            product.setStock(product.getStock() + quantity);
        }

        // Always marked: if the decrease never arrived, a late decrease with this key is ignored
        idempotencyStore.markRestored(key);
    }
}
