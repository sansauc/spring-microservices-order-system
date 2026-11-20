package com.sansa.service_customer.service;

import com.sansa.service_customer.model.Customer;

import java.util.List;

public interface CustomerService {

    List<Customer> findAll();

    Customer findById(Long id);

    void save(Customer customer);

}
