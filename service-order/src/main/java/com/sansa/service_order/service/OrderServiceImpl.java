package com.sansa.service_order.service;

import com.sansa.service_order.dto.CreateOrderRequest;
import com.sansa.service_order.dto.CustomerResponse;
import com.sansa.service_order.dto.OrderItemRequest;
import com.sansa.service_order.dto.ProductResponse;
import com.sansa.service_order.model.Order;
import com.sansa.service_order.model.OrderItem;
import com.sansa.service_order.model.Status;
import com.sansa.service_order.repository.OrderRepository;
import com.sansa.service_order.utils.exceptions.InsufficientStockException;
import com.sansa.service_order.utils.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import java.util.ArrayList;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService{

    private final OrderRepository orderRepository;
    private final CustomerService customerService;
    private final ProductService productService;

    @Override
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @Override
    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Order with ID: "+id+" not found"));
    }

    @Override
    public List<Order> findOrdersByCustomerId(Long customerId) {

        log.info("Validate the Customer with ID: {}",customerId);

        CustomerResponse customer = customerService.findCustomerById(customerId).orElseThrow(
                ()-> new ResourceNotFoundException("The customer with ID: " + customerId +" was not found"));


        log.info("obtaining the customer's list of orders");

        List<Order> orders = orderRepository.findOrdersByCustomerId(customer.id());

        return orders;
    }

    @Override
    public void create(CreateOrderRequest request) {
        log.info("Validate the request");

        log.info("Check and validate the customer");
        CustomerResponse customer = customerService.findCustomerById(request.customerId()).orElseThrow(
                ()-> new ResourceNotFoundException("The customer with ID: " + request.customerId() +" was not found"));

        log.info("Create the order");
        Order newOrder = new Order();

        newOrder.setId(request.id());
        newOrder.setCustomerId(customer.id());
        newOrder.setDate(LocalDate.now());
        newOrder.setStatus(Status.PENDING);

        BigDecimal totalPrice = BigDecimal.ZERO;

        log.info("Check products, verify stock, and calculate amounts");

        for(OrderItemRequest itemRequest : request.items()){

            if(itemRequest.quantity() <= 0){
                throw new RuntimeException("The quantity must be greater than 0.");
            }

            log.info("Validate product");

            ProductResponse product = productService.findProductById(itemRequest.productId()).
                    orElseThrow(()->new ResourceNotFoundException("The product with ID: "+itemRequest.productId()+" was not found"));

            if(product.stock() < itemRequest.quantity()){
                throw new InsufficientStockException("Insufficient quantity for the product with ID: " + product.id());
            }

            BigDecimal price = product.price();

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(itemRequest.quantity()));

            log.info("Create OrderItem");
            OrderItem orderItem = new OrderItem();
            orderItem.setId(ThreadLocalRandom.current().nextLong(1, 100));// Generates an ID so it does not have to be sent in the JSON
            orderItem.setProductId(product.id());
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setPrice(price);

            newOrder.getItems().add(orderItem);
            totalPrice = totalPrice.add(subtotal);

        }
        log.info("Calculate the total");
        newOrder.setTotal(totalPrice);

        orderRepository.save(newOrder);

        log.info("Decrease the stock of the products");
        // Items whose decrease was already ATTEMPTED. Used to know what to compensate if something fails.
        List<OrderItem> attempted = new ArrayList<>();
        try {
            for(OrderItem orderItem : newOrder.getItems()){
                // Added BEFORE calling decreaseStock, so the item that fails is also recorded.
                // On a timeout we do not know whether product applied the decrease; compensating it is safe because
                // increaseStock is idempotent with the same key: if the decrease was never applied, it does not restore anything extra.
                attempted.add(orderItem);
                productService.decreaseStock(orderItem.getProductId(), orderItem.getQuantity(),
                        buildIdempotencyKey(newOrder, orderItem));
            }
        } catch (RuntimeException e) {
            log.warn("Stock decrease failed for order {}. Compensating {} item(s). Cause: {}",
                    newOrder.getId(), attempted.size(), e.getMessage());

            boolean compensated = compensateStock(newOrder, attempted);

            // If everything was restored, the order ends cleanly as CANCELLED. If any compensation failed,
            // CANCELLED would be a lie (stock would be lost), so it is marked COMPENSATION_FAILED so that
            // it gets detected and reconciled manually.
            newOrder.setStatus(compensated ? Status.CANCELLED : Status.COMPENSATION_FAILED);
            orderRepository.save(newOrder);

            // The ORIGINAL exception is rethrown (not wrapped in another) because the GlobalExceptionHandler
            // decides the HTTP response by its type (e.g. InsufficientStockException -> 409).
            throw e;
        }

        newOrder.setStatus(Status.CONFIRMED);
        log.info("saved the new orden");
        orderRepository.save(newOrder);
    }

    // The key is deterministic: on a retry of the same order and product, product recognizes the repetition and does not decrease again.
    // The compensation (increaseStock) uses this SAME key so product knows which decrease it is reverting.
    private String buildIdempotencyKey(Order order, OrderItem item) {
        return "order-" + order.getId() + "-product-" + item.getProductId();
    }

    // Restores the stock of the attempted items. Returns true only if every compensation succeeded.
    private boolean compensateStock(Order order, List<OrderItem> attempted) {
        boolean allCompensated = true;
        // Reverse order: in a saga the steps are undone from last to first, like a stack,
        // reverting them in the opposite order to the one they were applied in.
        for (int i = attempted.size() - 1; i >= 0; i--) {
            OrderItem item = attempted.get(i);
            try {
                productService.increaseStock(item.getProductId(), item.getQuantity(), buildIdempotencyKey(order, item));
            } catch (RuntimeException ex) {
                // A failure does NOT break the loop: the stock of the other products must still be restored;
                // we only record that the compensation was not complete.
                allCompensated = false;
                log.error("Could not restore stock for product {} of order {}. Cause: {}",
                        item.getProductId(), order.getId(), ex.getMessage());
            }
        }
        return allCompensated;
    }

}
