package com.sansa.service_order.service;

import com.sansa.service_order.dto.CreateOrderRequest;
import com.sansa.service_order.model.Order;

import java.util.List;

public interface OrderService {

    List<Order> findAll();

    Order findById(Long id);

    List<Order> findOrdersByCustomerId(Long customerId);

    void create(CreateOrderRequest order);

}
