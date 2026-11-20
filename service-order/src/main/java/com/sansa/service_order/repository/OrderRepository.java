package com.sansa.service_order.repository;

import com.sansa.service_order.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class OrderRepository {

    private final List<Order> orders = new ArrayList<>();

    public List<Order> findAll(){ return orders;}

    public Optional<Order> findById(Long id){
        return orders.stream()
                .filter(order -> order.getId().equals(id))
                .findFirst();
    }

    public List<Order> findOrdersByCustomerId(Long customerID){
        return orders.stream()
                .filter(order -> order.getCustomerId().equals(customerID))
                .toList();
    }

    // synchronized because "orders" is an ArrayList, which is NOT thread-safe: each HTTP request runs on its own
    // thread, and if two save() calls run add()/set() at the same time, one write can overwrite the other and the order is lost
    // (seen with 6 simultaneous orders: only 2 were stored). With synchronized only one thread enters at a time and the
    // scan + add/set becomes atomic. With a real database the engine would guarantee this (primary key/transaction).
    public synchronized void save(Order order){

        for(int i=0; i<orders.size(); i++){
            if (orders.get(i).getId().equals(order.getId())){
                orders.set(i, order);
                return;
            }
        }
        orders.add(order);
    }

}
