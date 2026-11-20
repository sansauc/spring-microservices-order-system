package com.sansa.service_product.service;

import com.sansa.service_product.model.IdempotencyStore;
import com.sansa.service_product.model.Product;
import com.sansa.service_product.repository.ProductRepository;
import com.sansa.service_product.utils.exceptions.InsufficientStockException;
import com.sansa.service_product.utils.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product laptop;

    @BeforeEach
    void setUp(){
        laptop = Product.builder()
                .id(1L)
                .name("Laptop")
                .price(new BigDecimal("500000.00"))
                .stock(10)
                .build();
    }

    /**
     * Happy path: subtracts the quantity from the stock (10 - 2 = 8) and marks the idempotency key as processed.
     */
    @Test
    void decreaseStock_descuentaElStock_yMarcaLaClaveComoProcesada(){

        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));

        productService.decreaseStock(1L, 2, "order-1-product-1");

        assertThat(laptop.getStock()).isEqualTo(8);

        verify(idempotencyStore).markProcessed("order-1-product-1");
    }

    /**
     * If the key was already processed (a retry) nothing happens: the stock stays the same and the repository is not
     * even queried.
     */
    @Test
    void decreaseStock_conClaveYaProcesada_noTocaElStock(){

        when(idempotencyStore.alreadyProcessed("k")).thenReturn(true);

        productService.decreaseStock(1L, 2, "k");

        assertThat(laptop.getStock()).isEqualTo(10);
        verifyNoInteractions(productRepository);
        verify(idempotencyStore, never()).markProcessed(any());
    }

    /**
     * A quantity of 0 throws IllegalArgumentException and the key is not marked.
     */
    @Test
    void decreaseStock_conCantidadCero_lanzaIllegalArgument(){

        assertThatThrownBy(() -> productService.decreaseStock(1L, 0, "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The quantity must be greater than 0.");
        verify(idempotencyStore,never()).markProcessed(any());

    }

    /**
     * If more is requested than available (50 vs 10), InsufficientStockException is thrown, nothing is decreased and
     * the key is NOT marked, so a later retry stays valid.
     */
    @Test
    void decreaseStock_conStockInsuficiente_lanzaException_noDescuenta_yNoMarcaLaClave(){

        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));

        assertThatThrownBy(() -> productService.decreaseStock(1L, 50, "k"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock for the product with ID: 1");
        assertThat(laptop.getStock()).isEqualTo(10);
        verify(idempotencyStore, never()).markProcessed(any());
    }

    /**
     * Decreasing the stock of a product that does not exist throws ResourceNotFoundException.
     */
    @Test
    void decreaseStock_conProductoInexistente_lanzaResourceNotFound(){

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> productService.decreaseStock(99L, 1, "k"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * findAll returns the repository's list, with its 2 products in the same order.
     */
    @Test
    void findAll_devuelveLaListaDelRepositorio(){

        Product mouse = Product.builder()
                .id(2L)
                .name("Mouse")
                .price(new BigDecimal("35000.00"))
                .stock(20)
                .build();

        List<Product> products = List.of(laptop, mouse);

        when(productRepository.findAll()).thenReturn(products);

        List<Product> result = productService.findAll();

        assertThat(result)
                .hasSize(2)
                .containsExactly(laptop, mouse);
    }

    /**
     * findById for an existing product returns that product.
     */
    @Test
    void findById_existente_devuelveElProducto(){

        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));

        Product result = productService.findById(1L);

        assertThat(result).isEqualTo(laptop);
    }

    /**
     * findById for a product that does not exist throws ResourceNotFoundException with the expected message.
     */
    @Test
    void findById_inexistente_lanzaResourceNotFound(){

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product whit id 99 not found");
    }

    /**
     * save simply delegates to the repository with the same product.
     */
    @Test
    void save_delegaEnElRepositorio(){

        productService.save(laptop);

        verify(productRepository).save(laptop);
    }


    @Test
    void increaseStock_siElDescuentoSeProceso_devuelveElStock_yMarcaLaClaveComoRestaurada(){

        when(productRepository.findById(1L)).thenReturn(Optional.of(laptop));
        when(idempotencyStore.alreadyProcessed("k")).thenReturn(true);

        productService.increaseStock(1L, 2, "k");

        assertThat(laptop.getStock()).isEqualTo(12);
        verify(idempotencyStore).markRestored("k");
    }

    /**
     * If the key was already restored (a compensation retry) nothing happens: no stock is added and the repository
     * is not queried.
     */
    @Test
    void increaseStock_conClaveYaRestaurada_noHaceNada(){

        when(idempotencyStore.alreadyRestored("k")).thenReturn(true);

        productService.increaseStock(1L, 2, "k");

        assertThat(laptop.getStock()).isEqualTo(10);
        verifyNoInteractions(productRepository);
        verify(idempotencyStore, never()).markRestored(any());
    }

    /**
     * If the decrease with that key never arrived, nothing is added but the key is marked as restored (a
     * "tombstone"), so a late decrease with the same key is ignored.
     */
    @Test
    void increaseStock_siElDescuentoNuncaSeProceso_noTocaElStock_peroMarcaLaClave(){

        productService.increaseStock(1L, 2, "k");

        assertThat(laptop.getStock()).isEqualTo(10);
        verifyNoInteractions(productRepository);
        verify(idempotencyStore).markRestored("k");
    }

    /**
     * A quantity of 0 throws IllegalArgumentException and the key is not marked as restored.
     */
    @Test
    void increaseStock_conCantidadCero_lanzaIllegalArgument(){

        assertThatThrownBy(() -> productService.increaseStock(1L, 0, "k"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The quantity must be greater than 0.");
        verify(idempotencyStore, never()).markRestored(any());
    }

    /**
     * If the key was already restored, a late decrease with that key is ignored: nothing is decreased and the key is
     * not marked as processed.
     */
    @Test
    void decreaseStock_conClaveYaRestaurada_noDescuenta(){

        when(idempotencyStore.alreadyRestored("k")).thenReturn(true);

        productService.decreaseStock(1L, 2, "k");

        assertThat(laptop.getStock()).isEqualTo(10);
        verifyNoInteractions(productRepository);
        verify(idempotencyStore, never()).markProcessed(any());
    }


}
