package com.sansa.service_product.repository;

import com.sansa.service_product.model.Product;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final List<Product> products = new ArrayList<>();

    public List<Product> findAll(){
        return products;
    }

    public Optional<Product> findById(Long id){
        return products.stream()
                .filter(product -> product.getId().equals(id))
                .findFirst();
    }

    public void save(Product product){
        products.add(product);
    }

}
