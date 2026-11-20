package com.sansa.service_customer.repository;

import com.sansa.service_customer.model.Customer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class CustomerRepository {

    private final List<Customer> customers = new ArrayList<>();

    public List<Customer> findAll(){return customers;}

    public Optional<Customer> findById(Long id){
        return customers.stream()
                .filter(customer -> customer.getId().equals(id))
                .findFirst();
    }

    public void save(Customer customer){customers.add(customer);}


}
