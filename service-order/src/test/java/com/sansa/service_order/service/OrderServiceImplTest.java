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
import com.sansa.service_order.utils.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CustomerResponse customer;
    private ProductResponse laptop;
    private ProductResponse mouse;

    @BeforeEach
    void setUp() {
        customer = new CustomerResponse(1L, "Cliente 1", "cliente@gmail.com");
        laptop = new ProductResponse(1L, "Laptop", new BigDecimal("100.00"), 10);
        mouse  = new ProductResponse(2L, "Mouse",  new BigDecimal("50.50"), 10);
    }

    /**
     * Happy path: the order ends up saved with id, customer, date and CONFIRMED status, with the correct total
     * (200.00 + 151.50 = 351.50) and 2 items.
     */
    @Test
    void create_guardaOrdenConfirmada_conTotalCorrecto(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),    // 100.00 * 2 = 200.00
                new OrderItemRequest(2L, 3)));  //  50.50 * 3 = 151.50

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        orderService.create(request);

        // The order is now saved twice (PENDING before decreasing and CONFIRMED at the end). It is the same instance,
        // so the last captured value already reflects the final state.
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(2)).save(captor.capture());
        Order saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getCustomerId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(Status.CONFIRMED);
        assertThat(saved.getDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getTotal()).isEqualByComparingTo("351.50");
        assertThat(saved.getItems()).hasSize(2);


    }

    /**
     * decreaseStock is called for each item with the deterministic key "order-{orderId}-product-{productId}".
     */
    @Test
    void create_descuentaElStockDeCadaProducto_conClaveDeIdempotencia(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        orderService.create(request);

        verify(productService).decreaseStock(1L, 2, "order-10-product-1");
        verify(productService).decreaseStock(2L, 3, "order-10-product-2");

    }

    /**
     * If the customer does not exist, ResourceNotFoundException is thrown before products are queried or anything is
     * saved.
     */
    @Test
    void create_conClienteInexistente_lanzaResourceNotFound_yNoTocaProductosNiRepositorio(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 99L, List.of(new OrderItemRequest(1L, 2)));

        when(customerService.findCustomerById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The customer with ID: 99 was not found");

        verifyNoInteractions(productService);
        verifyNoInteractions(orderRepository);

    }

    /**
     * If a product does not exist, ResourceNotFoundException is thrown without decreasing stock or saving the order.
     */
    @Test
    void create_conProductoInexistente_lanzaResourceNotFound_sinDescontarNiGuardar(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(new OrderItemRequest(99L, 2)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The product with ID: 99 was not found");


        verify(productService, never()).decreaseStock(anyLong(), anyInt(), anyString());
        verifyNoInteractions(orderRepository);
    }

    /**
     * If the requested quantity exceeds the stock (20 vs 10), InsufficientStockException is thrown during
     * validation, without decreasing or saving anything.
     */
    @Test
    void create_conStockInsuficiente_lanzaInsufficientStock_sinDescontarNiGuardar(){

        // laptop has stock 10, an attempt is made to buy 20
        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(new OrderItemRequest(1L, 20)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));

        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient quantity for the product with ID: 1");

        verify(productService, never()).decreaseStock(anyLong(), anyInt(), anyString());
        verifyNoInteractions(orderRepository);
    }

    /**
     * If the 2nd item has an unknown product, the 1st (which was valid) is not decreased either: everything is
     * validated before touching stock.
     */
    @Test
    void create_conSegundoItemInexistente_noDescuentaNingunItem() {

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(99L, 1)
        ));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The product with ID: 99 was not found");

        // item 1 was valid, but it was NOT decreased
        verify(productService, never()).decreaseStock(anyLong(), anyInt(), anyString());
        verifyNoInteractions(orderRepository);
    }

    /**
     * If the 2nd item has insufficient stock, the 1st is not decreased either: everything is validated before
     * touching stock.
     */
    @Test
    void create_conSegundoItemSinStock_noDescuentaNingunItem(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 11)
        ));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient quantity for the product with ID: 2");

        verify(productService, never()).decreaseStock(anyLong(), anyInt(), anyString());
        verifyNoInteractions(orderRepository);
    }

    /**
     * An item with quantity 0 throws RuntimeException; the validation happens before the product is queried and
     * nothing is touched.
     */
    @Test
    void create_conCantidadCero_lanzaRuntimeException_sinTocarProductos(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 0)
        ));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("The quantity must be greater than 0.");

        verifyNoInteractions(productService);  // the validation happens before the product is queried
        verifyNoInteractions(orderRepository);
    }

    /**
     * Saga: if the decrease of the 2nd item fails, both items are compensated in reverse order (first the 2nd, which
     * may have been applied on a timeout, then the 1st). The order goes through PENDING and ends as CANCELLED, never
     * CONFIRMED.
     */
    @Test
    void create_siDecreaseStockFallaEnElSegundoItem_compensaAmbosItems_yCancelaLaOrden(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)
        ));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        // Records the order status at the moment of each save (it is the same instance and it mutates later)
        List<Status> statuses = captureStatusesOnSave();

        // The decrease of item 1 works (declared explicitly because of strict stubs)
        doNothing().when(productService).decreaseStock(1L, 2, "order-10-product-1");

        doThrow(new ServiceUnavailableException("Could not decrease stock for product 2"))
                .when(productService).decreaseStock(2L, 3, "order-10-product-2");


        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Could not decrease stock for product 2");

        // The saga compensates in reverse order: first item 2 (the one that failed; on a timeout it may have been applied) and then item 1
        InOrder inOrder = inOrder(productService);
        inOrder.verify(productService).increaseStock(2L, 3, "order-10-product-2");
        inOrder.verify(productService).increaseStock(1L, 2, "order-10-product-1");

        // PENDING is saved at the start and CANCELLED when the compensation finishes; never CONFIRMED
        verify(orderRepository, times(2)).save(any(Order.class));
        assertThat(statuses).containsExactly(Status.PENDING, Status.CANCELLED);

    }

    /**
     * If service-customer is down, ServiceUnavailableException is propagated without touching products or saving the
     * order.
     */
    @Test
    void create_conCustomerServiceCaido_propagaServiceUnavailable_sinTocarProductosNiRepositorio(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2)
        ));

        when(customerService.findCustomerById(1L))
                .thenThrow(new ServiceUnavailableException("Customer service is currently unavailable"));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Customer service is currently unavailable");

        verifyNoInteractions(productService);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void create_guardaLosItemsConProductIdCantidadYPrecio(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)
        ));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        orderService.create(request);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);

        // Two saves of the same instance: PENDING at the start and CONFIRMED at the end
        verify(orderRepository, times(2)).save(captor.capture());

        assertThat(captor.getValue().getItems())
                .extracting(OrderItem::getProductId, OrderItem::getQuantity, OrderItem::getPrice)
                .containsExactly(
                        tuple(1L, 2, new BigDecimal("100.00")),
                        tuple(2L, 3, new BigDecimal("50.50")));

    }

    @Test
    void create_guardaLaOrdenFinalDespuesDeDescontarElStock() {

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        orderService.create(request);

        InOrder inOrder = inOrder(productService, orderRepository);
        inOrder.verify(productService).decreaseStock(1L, 2, "order-10-product-1");
        inOrder.verify(productService).decreaseStock(2L, 3, "order-10-product-2");
        inOrder.verify(orderRepository).save(any(Order.class));
    }

    // ---------- saga: statuses and stock compensation ----------

    @Test
    void create_guardaLaOrdenPendienteAntesDeDescontar_yLaConfirmaAlFinal(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        List<Status> statuses = captureStatusesOnSave();

        orderService.create(request);

        // The order exists (PENDING) before touching the stock: so decreased stock never goes untracked
        InOrder inOrder = inOrder(orderRepository, productService);
        inOrder.verify(orderRepository).save(any(Order.class));
        inOrder.verify(productService).decreaseStock(1L, 2, "order-10-product-1");

        assertThat(statuses).containsExactly(Status.PENDING, Status.CONFIRMED);
        // Happy path: there is nothing to compensate
        verify(productService, never()).increaseStock(anyLong(), anyInt(), anyString());
    }

    /**
     * Saga: if the decrease of the 1st item fails, only that item is compensated (it may have been applied on a
     * timeout). The 2nd is neither attempted nor compensated. The order ends as CANCELLED.
     */
    @Test
    void create_siFallaElPrimerItem_soloCompensaEseItem_yNoIntentaLosSiguientes(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        List<Status> statuses = captureStatusesOnSave();

        doThrow(new ServiceUnavailableException("Could not decrease stock for product 1"))
                .when(productService).decreaseStock(1L, 2, "order-10-product-1");

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Could not decrease stock for product 1");

        // Item 1 failed: it is compensated in case it was applied on a timeout. Item 2 was never attempted, so there is nothing to restore
        verify(productService).increaseStock(1L, 2, "order-10-product-1");
        verify(productService, never()).decreaseStock(eq(2L), anyInt(), anyString());
        verify(productService, never()).increaseStock(eq(2L), anyInt(), anyString());

        assertThat(statuses).containsExactly(Status.PENDING, Status.CANCELLED);
    }

    /**
     * Saga: if a compensation also fails, the loop is not interrupted (the other item is still restored), the order
     * ends as COMPENSATION_FAILED instead of CANCELLED, and the ORIGINAL exception from the decrease is propagated.
     */
    @Test
    void create_siFallaLaCompensacion_marcaCompensationFailed_sigueConLosDemasItems_yPropagaLaExcepcionOriginal(){

        CreateOrderRequest request = new CreateOrderRequest(10L, 1L, List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 3)));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(productService.findProductById(1L)).thenReturn(Optional.of(laptop));
        when(productService.findProductById(2L)).thenReturn(Optional.of(mouse));

        List<Status> statuses = captureStatusesOnSave();

        doNothing().when(productService).decreaseStock(1L, 2, "order-10-product-1");
        doThrow(new ServiceUnavailableException("Could not decrease stock for product 2"))
                .when(productService).decreaseStock(2L, 3, "order-10-product-2");

        // The compensation of item 2 fails, the one of item 1 works
        doThrow(new ServiceUnavailableException("Could not restore stock for product 2"))
                .when(productService).increaseStock(2L, 3, "order-10-product-2");
        doNothing().when(productService).increaseStock(1L, 2, "order-10-product-1");

        // The ORIGINAL exception from the decrease is propagated, not the compensation's
        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Could not decrease stock for product 2");

        // A failed compensation does not break the loop: item 1 is still restored
        verify(productService).increaseStock(2L, 3, "order-10-product-2");
        verify(productService).increaseStock(1L, 2, "order-10-product-1");

        // CANCELLED would be a lie (stock is left unrestored), so the order ends as COMPENSATION_FAILED
        assertThat(statuses).containsExactly(Status.PENDING, Status.COMPENSATION_FAILED);
    }

    // ---------- findAll ----------

    @Test
    void findAll_devuelveLasOrdenesDelRepositorio(){

        List<Order> orders = List.of(buildOrder(1L, 1L), buildOrder(2L, 1L));

        when(orderRepository.findAll()).thenReturn(orders);

        List<Order> result = orderService.findAll();

        assertThat(result).isSameAs(orders);
        verify(orderRepository).findAll();
    }

    /**
     * With no orders in the repository, findAll returns an empty list.
     */
    @Test
    void findAll_sinOrdenes_devuelveListaVacia(){

        when(orderRepository.findAll()).thenReturn(List.of());

        assertThat(orderService.findAll()).isEmpty();
    }

    // ---------- findById ----------

    @Test
    void findById_conOrdenExistente_devuelveLaOrden(){

        Order order = buildOrder(5L, 1L);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        assertThat(orderService.findById(5L)).isSameAs(order);
    }

    /**
     * An order that does not exist throws ResourceNotFoundException with the message "Order with ID: 99 not found".
     */
    @Test
    void findById_conOrdenInexistente_lanzaResourceNotFound(){

        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order with ID: 99 not found");
    }

    // ---------- findOrdersByCustomerId ----------

    @Test
    void findOrdersByCustomerId_conClienteExistente_devuelveSusOrdenes(){

        List<Order> orders = List.of(buildOrder(1L, 1L), buildOrder(2L, 1L));

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(orderRepository.findOrdersByCustomerId(1L)).thenReturn(orders);

        List<Order> result = orderService.findOrdersByCustomerId(1L);

        assertThat(result).isSameAs(orders);
    }

    /**
     * If the customer does not exist, ResourceNotFoundException is thrown without querying the order repository.
     */
    @Test
    void findOrdersByCustomerId_conClienteInexistente_lanzaResourceNotFound_sinTocarElRepositorio(){

        when(customerService.findCustomerById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.findOrdersByCustomerId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The customer with ID: 99 was not found");

        verifyNoInteractions(orderRepository);
    }

    /**
     * An existing customer with no orders gets an empty list (not an error).
     */
    @Test
    void findOrdersByCustomerId_conClienteSinOrdenes_devuelveListaVacia(){

        when(customerService.findCustomerById(1L)).thenReturn(Optional.of(customer));
        when(orderRepository.findOrdersByCustomerId(1L)).thenReturn(List.of());

        assertThat(orderService.findOrdersByCustomerId(1L)).isEmpty();
    }

    /**
     * If service-customer is down, ServiceUnavailableException is propagated without querying the order repository.
     */
    @Test
    void findOrdersByCustomerId_conCustomerServiceCaido_propagaServiceUnavailable_sinTocarElRepositorio(){

        when(customerService.findCustomerById(1L))
                .thenThrow(new ServiceUnavailableException("Customer service is currently unavailable"));

        assertThatThrownBy(() -> orderService.findOrdersByCustomerId(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Customer service is currently unavailable");

        verifyNoInteractions(orderRepository);
    }

    // Records the order status at the exact moment of each save. It is needed because create() saves
    // the SAME instance several times and a captor would only see the final state.
    private List<Status> captureStatusesOnSave(){
        List<Status> statuses = new ArrayList<>();
        doAnswer(invocation -> {
            statuses.add(invocation.<Order>getArgument(0).getStatus());
            return null;
        }).when(orderRepository).save(any(Order.class));
        return statuses;
    }

    private Order buildOrder(Long id, Long customerId){
        Order order = new Order();
        order.setId(id);
        order.setCustomerId(customerId);
        return order;
    }


}
