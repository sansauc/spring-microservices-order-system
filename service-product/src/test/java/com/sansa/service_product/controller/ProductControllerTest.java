package com.sansa.service_product.controller;

import com.sansa.service_product.model.Product;
import com.sansa.service_product.service.ProductService;
import com.sansa.service_product.utils.exceptions.InsufficientStockException;
import com.sansa.service_product.utils.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import org.springframework.http.MediaType;

import java.math.BigDecimal;

@WebMvcTest
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    /**
     * GET /products/{id} for an existing product: responds 200 with id, name and stock.
     */
    @Test
    void findById_devuelve200ConElProducto() throws Exception {

        Product laptop = Product.builder()
                .id(1L)
                .name("Laptop Lenovo")
                .price(new BigDecimal("550000.00"))
                .stock(10)
                .build();

        when(productService.findById(1L)).thenReturn(laptop);

        mockMvc.perform(get("/api/v1/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Laptop Lenovo"))
                .andExpect(jsonPath("$.stock").value(10));

    }

    /**
     * GET /products/{id} for a product that does not exist: responds 404 with the ApiError.
     */
    @Test
    void findById_conProductoInexistente_devuelve404ConApiError() throws Exception{

        when(productService.findById(99L)).thenThrow(new ResourceNotFoundException("Product whit id 99 not found"));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product whit id 99 not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/products/99"));
    }

    /**
     * POST /products with valid data: responds 201 with no body and the service's save is called.
     */
    @Test
    void createProduct_valido_devuelve201SinCuerpo() throws Exception{

        String body = """
                { "id": 1, "name": "Laptop Lenovo", "price": 1500.00, "stock": 10 }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        verify(productService).save(any(Product.class));

    }

    /**
     * POST /products with an empty name: responds 400 with the name field error and the service is not called.
     */
    @Test
    void createProduct_sinNombre_devuelve400ConErroresPorCampo_yNoLlamaAlServicio() throws Exception{

        String body = """
                { "id": 1, "name": "", "price": 1500.00, "stock": 10 }
                """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name[0]").value("name is required"));

        verifyNoInteractions(productService);
    }

    /**
     * PUT /products/{id}/stock with quantity and an Idempotency-Key header: responds 204 and the service receives
     * the id, the quantity and the key.
     */
    @Test
    void decreaseStock_valido_devuelve204_yPasaLaClaveDeIdempotencia() throws Exception{
        mockMvc.perform(put("/api/v1/products/5/stock")
                        .param("quantity", "3")
                        .header("Idempotency-Key", "order-1-product-5"))
                .andExpect(status().isNoContent());

        verify(productService).decreaseStock(5L, 3, "order-1-product-5");
    }

    /**
     * PUT /products/{id}/stock when the service throws InsufficientStockException: responds 409.
     */
    @Test
    void decreaseStock_conStockInsuficiente_devuelve409() throws Exception{

        doThrow(new InsufficientStockException("Insufficient stock for the product with ID: 5"))
                .when(productService).decreaseStock(5L, 1000, "k");

        mockMvc.perform(put("/api/v1/products/5/stock")
                        .param("quantity", "1000")
                        .header("Idempotency-Key", "k"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    /**
     * PUT /products/{id}/stock without the (required) Idempotency-Key header: responds 400.
     */
    @Test
    void decreaseStock_sinHeaderDeIdempotencia_devuelve400() throws Exception{

        mockMvc.perform(put("/api/v1/products/5/stock").param("quantity", "3"))
                .andExpect(status().isBadRequest());
    }

    /**
     * PUT /products/{id}/stock with quantity 0: validation (@Positive) fails, responds 400 with the quantity
     * parameter error and the service is not called.
     */
    @Test
    void decreaseStock_conCantidadCero_devuelve400_yNoLlamaAlServicio() throws Exception{

        mockMvc.perform(put("/api/v1/products/5/stock")
                        .param("quantity", "0")
                        .header("Idempotency-Key", "k"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.quantity[0]").value("quantity must be greater than 0"));

        verifyNoInteractions(productService);
    }

    // ---------- PUT /api/v1/products/{id}/stock/restore (saga compensation) ----------

    /**
     * PUT /products/{id}/stock/restore with quantity and an Idempotency-Key header: responds 204 and the service
     * receives the id, the quantity and the key.
     */
    @Test
    void increaseStock_valido_devuelve204_yPasaLaClaveDeIdempotencia() throws Exception{

        mockMvc.perform(put("/api/v1/products/5/stock/restore")
                        .param("quantity", "3")
                        .header("Idempotency-Key", "order-1-product-5"))
                .andExpect(status().isNoContent());

        verify(productService).increaseStock(5L, 3, "order-1-product-5");
    }

    /**
     * PUT /products/{id}/stock/restore without the (required) Idempotency-Key header: responds 400 and the service
     * is not called.
     */
    @Test
    void increaseStock_sinHeaderDeIdempotencia_devuelve400() throws Exception{

        mockMvc.perform(put("/api/v1/products/5/stock/restore").param("quantity", "3"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    /**
     * PUT /products/{id}/stock/restore with quantity 0: validation (@Positive) fails, responds 400 and the service
     * is not called.
     */
    @Test
    void increaseStock_conCantidadCero_devuelve400_yNoLlamaAlServicio() throws Exception{

        mockMvc.perform(put("/api/v1/products/5/stock/restore")
                        .param("quantity", "0")
                        .header("Idempotency-Key", "k"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    /**
     * PUT /products/{id}/stock/restore when the service throws ResourceNotFoundException (unknown product): responds
     * 404 with the ApiError.
     */
    @Test
    void increaseStock_conProductoInexistente_devuelve404ConApiError() throws Exception{

        doThrow(new ResourceNotFoundException("Product whit id 99 not found"))
                .when(productService).increaseStock(99L, 1, "k");

        mockMvc.perform(put("/api/v1/products/99/stock/restore")
                        .param("quantity", "1")
                        .header("Idempotency-Key", "k"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/products/99/stock/restore"));
    }
}
