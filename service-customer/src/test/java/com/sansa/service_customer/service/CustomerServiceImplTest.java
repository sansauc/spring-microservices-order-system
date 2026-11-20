package com.sansa.service_customer.service;

import com.sansa.service_customer.model.Customer;
import com.sansa.service_customer.repository.CustomerRepository;
import com.sansa.service_customer.utils.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Customer customer;

    @BeforeEach
    void setUp(){
        customer = Customer.builder()
                .id(1L)
                .name("Cliente 1")
                .email("cliente_prueba@gmail.com")
                .build();
    }

    /**
     * findAll returns the same list the repository provides, with its 2 customers in the same order.
     */
    @Test
    void findAll_devuelveLaListaDelRepositorio(){

        Customer customer2 = Customer.builder()
                .id(2L)
                .name("Cliente 2")
                .email("cliente_2_prueba@gmail.com")
                .build();

        List<Customer> customers = List.of(customer, customer2);

        when(customerRepository.findAll()).thenReturn(customers);

        List<Customer> result = customerService.findAll();

        assertThat(result)
                .hasSize(2)
                .containsExactly(customer, customer2);

    }

    /**
     * findById for an existing customer returns that customer.
     */
    @Test
    void findById_existente_devuelveElCliente(){

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        Customer result = customerService.findById(1L);

        assertThat(result).isEqualTo(customer);

    }

    /**
     * findById for a customer that does not exist throws ResourceNotFoundException with the expected message.
     */
    @Test
    void findById_inexistente_lanzaResourceNotFound(){

        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> customerService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client with id: 99 not found");
    }

    /**
     * save simply delegates to the repository with the same customer.
     */
    @Test
    void save_delegaEnElRepositorio(){

        customerService.save(customer);

        verify(customerRepository).save(customer);

    }
}
