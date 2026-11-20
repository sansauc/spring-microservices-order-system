package com.sansa.service_customer.service;

import com.sansa.service_customer.model.Customer;
import com.sansa.service_customer.repository.CustomerRepository;
import com.sansa.service_customer.utils.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CustomerServiceImpl implements CustomerService{

    private final CustomerRepository customerRepository;


    @Override
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    public Customer findById(Long id) {
        return customerRepository.findById(id).orElseThrow(()-> new ResourceNotFoundException("Client with id: "+id+" not found"));
    }

    @Override
    public void save(Customer customer) {
        customerRepository.save(customer);
    }

}
