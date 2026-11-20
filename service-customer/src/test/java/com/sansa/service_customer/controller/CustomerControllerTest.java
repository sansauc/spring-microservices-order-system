package com.sansa.service_customer.controller;

import com.sansa.service_customer.model.Customer;
import com.sansa.service_customer.service.CustomerService;
import com.sansa.service_customer.utils.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(CustomerController.class)
public class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    /**
     * GET /customers/{id} for an existing customer: responds 200 with id, name and email.
     */
    @Test
    void findById_devuelve200ConElCustomer() throws Exception{

        Customer customer = Customer.builder()
                .id(1L)
                .name("Customer 1")
                .email("customer_prueba@gmail.com")
                .build();

        when(customerService.findById(1L)).thenReturn(customer);

        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Customer 1"))
                .andExpect(jsonPath("$.email").value("customer_prueba@gmail.com"));

    }

    /**
     * POST /customers with valid data: responds 201 with no body and the service's save is called.
     */
    @Test
    void createCustomer_valido_devuelve201sinCuerpo() throws Exception{

        String body = """
                { "id": 1, "name": "Customer 1", "email": "customer_prueba@gmail.com" }
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        verify(customerService).save(any(Customer.class));
    }

    /**
     * POST /customers with an empty name: responds 400 with the name field error and the service is not called.
     */
    @Test
    void createCustomer_sinNombre_devuelve400ConErroresPorCampo_yNoLlamaAlServicio() throws Exception{

        String body = """
                { "id": 1, "name": "", "email": "customer_prueba@gmail.com" }
                """;
        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name[0]").value("name is required"));

        verifyNoInteractions(customerService);
    }

    /**
     * GET /customers/{id} for a customer that does not exist: responds 404 with the ApiError (status, error, message
     * and path).
     */
    @Test
    void findById_inexistente_devuelve404ConApiError() throws Exception{

        when(customerService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Client with id: 99 not found"));

        mockMvc.perform(get("/api/v1/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Client with id: 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/customers/99"));
    }

    /**
     * GET /customers: responds 200 with the list of customers.
     */
    @Test
    void findAll_devuelve200ConLaLista() throws Exception{

        Customer customer1 = Customer.builder().id(1L).name("Customer 1").email("customer_1@gmail.com").build();
        Customer customer2 = Customer.builder().id(2L).name("Customer 2").email("customer_2@gmail.com").build();

        when(customerService.findAll()).thenReturn(List.of(customer1, customer2));

        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Customer 1"))
                .andExpect(jsonPath("$[1].email").value("customer_2@gmail.com"));
    }

    /**
     * POST /customers with an email that has an invalid format: responds 400 and the service is not called.
     */
    @Test
    void createCustomer_emailMalFormado_devuelve400_yNoLlamaAlServicio() throws Exception{

        String body = """
                { "name": "Customer 1", "email": "no-es-un-email" }
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email[0]").value("email must be a valid address"));

        verifyNoInteractions(customerService);
    }

    /**
     * POST /customers with an empty email: responds 400 with "email is required" and the service is not called.
     */
    @Test
    void createCustomer_emailVacio_devuelve400_yNoLlamaAlServicio() throws Exception{

        String body = """
                { "name": "Customer 1", "email": "" }
                """;

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email[0]").value("email is required"));

        verifyNoInteractions(customerService);
    }

    /**
     * POST /customers with a name longer than 100 characters: responds 400 and the service is not called.
     */
    @Test
    void createCustomer_nombreMuyLargo_devuelve400_yNoLlamaAlServicio() throws Exception{

        String body = """
                { "name": "%s", "email": "customer_prueba@gmail.com" }
                """.formatted("a".repeat(101));

        mockMvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name[0]").value("name must not exceed 100 characters"));

        verifyNoInteractions(customerService);
    }

}
