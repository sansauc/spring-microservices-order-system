package com.sansa.service_order.controller;

import com.sansa.service_order.dto.CreateOrderRequest;
import com.sansa.service_order.dto.OrderItemRequest;
import com.sansa.service_order.model.Order;
import com.sansa.service_order.model.Status;
import com.sansa.service_order.service.OrderService;
import com.sansa.service_order.utils.exceptions.InsufficientStockException;
import com.sansa.service_order.utils.exceptions.ResourceNotFoundException;
import com.sansa.service_order.utils.exceptions.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest
public class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void findAll_devuelve200ConLaListaDeOrdenes() throws Exception {

        Order order1 = Order.builder()
                .id(1L)
                .customerId(1L)
                .date(LocalDate.of(2026, 1, 15))
                .status(Status.CONFIRMED)
                .total(new BigDecimal("351.50"))
                .build();

        Order order2 = Order.builder()
                .id(2L)
                .customerId(2L)
                .date(LocalDate.of(2026, 1, 16))
                .status(Status.CONFIRMED)
                .total(new BigDecimal("100.00"))
                .build();

        when(orderService.findAll()).thenReturn(List.of(order1, order2));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].customerId").value(1))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].total").value(351.50))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    /**
     * GET /orders with no stored orders: responds 200 with an empty list (not 404).
     */
    @Test
    void findAll_sinOrdenes_devuelve200ConListaVacia() throws Exception{

        when(orderService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void findById_devuelve200ConLaOrden() throws Exception {

        Order order1 = Order.builder()
                .id(1L)
                .customerId(1L)
                .date(LocalDate.of(2026, 1, 15))
                .status(Status.CONFIRMED)
                .total(new BigDecimal("351.50"))
                .build();

        when(orderService.findById(1L)).thenReturn(order1);

        mockMvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.total").value(351.50));

    }

    /**
     * GET /orders/{id} for an order that does not exist: the service throws ResourceNotFoundException and the
     * response is 404 with the ApiError (status, message and path).
     */
    @Test
    void findById_conOrdenInexistente_devuelve404ConApiError() throws Exception {

        when(orderService.findById(99L)).thenThrow(new ResourceNotFoundException("Order whit id 99 not found"));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Order whit id 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/99"));

    }

    /**
     * GET /orders/abc: the id is not numeric, Spring responds 400 and the service is never called.
     */
    @Test
    void findById_conIdNoNumerico_devuelve400_yNoLlamaAlServicio() throws Exception {

        mockMvc.perform(get("/api/v1/orders/abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    // ---------- GET /api/v1/orders/customer/{customerId} ----------

    @Test
    void findOrdersByCustomerId_devuelve200ConLasOrdenesDelCliente() throws Exception {

        Order order1 = Order.builder()
                .id(1L)
                .customerId(1L)
                .date(LocalDate.of(2026, 1, 15))
                .status(Status.CONFIRMED)
                .total(new BigDecimal("351.50"))
                .build();

        Order order2 = Order.builder()
                .id(2L)
                .customerId(1L)
                .date(LocalDate.of(2026, 1, 16))
                .status(Status.CONFIRMED)
                .total(new BigDecimal("100.00"))
                .build();

        when(orderService.findOrdersByCustomerId(1L)).thenReturn(List.of(order1, order2));

        mockMvc.perform(get("/api/v1/orders/customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].customerId").value(1))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].customerId").value(1));
    }

    /**
     * GET /orders/customer/{id} for a customer that does not exist: responds 404 with the ApiError.
     */
    @Test
    void findOrdersByCustomerId_conClienteInexistente_devuelve404ConApiError() throws Exception {

        when(orderService.findOrdersByCustomerId(99L))
                .thenThrow(new ResourceNotFoundException("The customer with ID: 99 was not found"));

        mockMvc.perform(get("/api/v1/orders/customer/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The customer with ID: 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/customer/99"));
    }

    /**
     * GET /orders/customer/{id} while service-customer is down: the service throws ServiceUnavailableException and
     * the response is 503 with the ApiError.
     */
    @Test
    void findOrdersByCustomerId_conCustomerServiceCaido_devuelve503ConApiError() throws Exception {

        when(orderService.findOrdersByCustomerId(1L))
                .thenThrow(new ServiceUnavailableException("Customer service is currently unavailable"));

        mockMvc.perform(get("/api/v1/orders/customer/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Customer service is currently unavailable"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders/customer/1"));
    }

    // ---------- POST /api/v1/orders ----------

    @Test
    void create_valido_devuelve201SinCuerpo_yPasaElRequestAlServicio() throws Exception {

        String body = """
                {
                  "id": 10,
                  "customerId": 1,
                  "items": [
                    { "productId": 1, "quantity": 2 },
                    { "productId": 2, "quantity": 3 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        // Capture the request the service receives to check the JSON was deserialized correctly
        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).create(captor.capture());

        CreateOrderRequest request = captor.getValue();

        assertThat(request.id()).isEqualTo(10L);
        assertThat(request.customerId()).isEqualTo(1L);
        assertThat(request.items())
                .extracting(OrderItemRequest::productId, OrderItemRequest::quantity)
                .containsExactly(
                        tuple(1L, 2),
                        tuple(2L, 3));
    }

    /**
     * POST /orders without the id field: validation fails, responds 400 with the id field error and the service is
     * not called.
     */
    @Test
    void create_sinId_devuelve400ConErroresPorCampo_yNoLlamaAlServicio() throws Exception {

        String body = """
                { "customerId": 1, "items": [ { "productId": 1, "quantity": 2 } ] }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"))
                .andExpect(jsonPath("$.errors.id[0]").value("ID is required"));

        verifyNoInteractions(orderService);
    }

    /**
     * POST /orders with a negative customerId: validation fails and responds 400 with the customerId field error.
     */
    @Test
    void create_conCustomerIdNegativo_devuelve400ConErroresPorCampo() throws Exception {

        String body = """
                { "id": 10, "customerId": -1, "items": [ { "productId": 1, "quantity": 2 } ] }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.customerId[0]").value("customerId must be positive"));

        verifyNoInteractions(orderService);
    }

    /**
     * POST /orders with an empty items list: validation fails and responds 400 with the items field error.
     */
    @Test
    void create_conItemsVacio_devuelve400ConErroresPorCampo() throws Exception {

        String body = """
                { "id": 10, "customerId": 1, "items": [] }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.items[0]").value("items must contain at least one item"));

        verifyNoInteractions(orderService);
    }

    /**
     * POST /orders with an item whose quantity is 0: validation fails and responds 400 with the error on
     * items[0].quantity.
     */
    @Test
    void create_conCantidadCero_devuelve400ConErrorEnElItem() throws Exception {

        String body = """
                { "id": 10, "customerId": 1, "items": [ { "productId": 1, "quantity": 0 } ] }
                """;

        // The error key is "items[0].quantity": since it has brackets and a dot, in jsonPath it goes inside ['...']
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors['items[0].quantity'][0]").value("quantity must be greater than 0"));

        verifyNoInteractions(orderService);
    }

    /**
     * POST /orders with several invalid fields at once: responds 400 with ALL the errors together (id, customerId
     * and productId), not just the first one.
     */
    @Test
    void create_conVariosCamposInvalidos_devuelve400ConTodosLosErrores() throws Exception {

        // negative id, missing customerId and negative productId in the first item
        String body = """
                { "id": -5, "items": [ { "productId": -1, "quantity": 2 } ] }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors.id[0]").value("ID must be positive"))
                .andExpect(jsonPath("$.errors.customerId[0]").value("customerId is required"))
                .andExpect(jsonPath("$.errors['items[0].productId'][0]").value("productId must be positive"));

        verifyNoInteractions(orderService);
    }

    /**
     * POST /orders with unreadable JSON (unclosed brace): responds 400 and the service is not called.
     */
    @Test
    void create_conJsonMalFormado_devuelve400_yNoLlamaAlServicio() throws Exception {

        // The closing brace is missing: the JSON cannot be read
        String body = """
                { "id": 10, "customerId": 1, "items": [
                """;

        // This 400 is built by Spring (the handler rethrows HttpMessageNotReadableException), it has no ApiError format
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    /**
     * POST /orders when the service throws InsufficientStockException: responds 409 with the ApiError.
     */
    @Test
    void create_conStockInsuficiente_devuelve409ConApiError() throws Exception {

        String body = """
                { "id": 10, "customerId": 1, "items": [ { "productId": 1, "quantity": 20 } ] }
                """;

        // create is void: doThrow().when() is used instead of when().thenThrow()
        doThrow(new InsufficientStockException("Insufficient quantity for the product with ID: 1"))
                .when(orderService).create(any(CreateOrderRequest.class));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Insufficient quantity for the product with ID: 1"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }

    /**
     * POST /orders when the service throws ResourceNotFoundException (unknown product): responds 404 with the
     * ApiError.
     */
    @Test
    void create_conProductoInexistente_devuelve404ConApiError() throws Exception {

        String body = """
                { "id": 10, "customerId": 1, "items": [ { "productId": 99, "quantity": 2 } ] }
                """;

        doThrow(new ResourceNotFoundException("The product with ID: 99 was not found"))
                .when(orderService).create(any(CreateOrderRequest.class));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The product with ID: 99 was not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/orders"));
    }


}
