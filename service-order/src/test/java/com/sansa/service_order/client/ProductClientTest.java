package com.sansa.service_order.client;

import com.github.tomakehurst.wiremock.http.Fault;
import com.sansa.service_order.service.AbstractWireMockTest;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sansa.service_order.dto.ProductResponse;

import java.util.Optional;

public class ProductClientTest extends AbstractWireMockTest {

    private static final String PRODUCT_URL = "/api/v1/products/1";
    private static final String STOCK_URL = "/api/v1/products/1/stock";

    @Autowired
    private ProductClient productClient;


    /**
     * Happy path: the product exists (200 with JSON). Verifies the path is correct and that the JSON is deserialized
     * into ProductResponse with all its fields.
     */
    @Test
    void findProductById_returnsProduct_when200(){

        productMock.stubFor(get(urlPathEqualTo(PRODUCT_URL))
                .willReturn(okJson("""
                        {"id": 1, "name": "Teclado", "price": 25.50, "stock": 10}
                        """)));
        Optional<ProductResponse> result = productClient.findProductById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Teclado");
        assertThat(result.get().stock()).isEqualTo(10);
    }

    /**
     * The product does not exist (404). Thanks to dismiss404: true, Feign does not throw and returns
     * Optional.empty().
     */
    @Test
    void findProductById_returnsEmpty_when404(){

        productMock.stubFor(get(urlPathEqualTo(PRODUCT_URL))
                .willReturn(notFound()));

        Optional<ProductResponse> result = productClient.findProductById(1L);

        assertThat(result).isEmpty();
    }

    /**
     * Server error (500). Feign translates it to FeignServerException, the type of error that retry and the circuit
     * breaker do treat as a failure.
     */
    @Test
    void findProductById_throwsServerException_when500(){

        productMock.stubFor(get(urlPathEqualTo(PRODUCT_URL))
                .willReturn(serverError()));

        assertThatThrownBy(() -> productClient.findProductById(1L))
                .isInstanceOf(FeignException.FeignServerException.class);
    }

    /**
     * Read timeout: the stub responds with a 600 ms delay and the test readTimeout is 300 ms, so Feign stops waiting
     * and throws RetryableException.
     */
    @Test
    void findProductById_throwsRetryableException_whenTimeout(){

        productMock.stubFor(get(urlPathEqualTo(PRODUCT_URL))
                .willReturn(okJson("""
                        {"id": 1, "name": "Teclado", "price": 25.50, "stock": 10}
                        """).withFixedDelay(600)));
        assertThatThrownBy(() -> productClient.findProductById(1L))
                .isInstanceOf(RetryableException.class);
    }

    /**
     * Network failure: the server cuts the connection mid-response. Feign treats it as an I/O failure and throws
     * RetryableException.
     */
    @Test
    void findProductById_throwsRetryableException_whenConnectionReset(){
        productMock.stubFor(get(urlPathEqualTo(PRODUCT_URL))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> productClient.findProductById(1L))
                .isInstanceOf(RetryableException.class);
    }

    //Decrease Stock

    /**
     * Verifies the outgoing request to product: PUT to the right path, with the quantity query param and the
     * Idempotency-Key header. exactly(1) also ensures Feign did not retry on its own (retries come only from
     * Resilience4j).
     */
    @Test
    void decreaseStock_sendsQuantityAndIdempotencyKey() {

        productMock.stubFor(put(urlPathEqualTo(STOCK_URL))
                .willReturn(ok()));

        productClient.decreaseStock(1L, 2, "order-1-product-1");

        productMock.verify(exactly(1), putRequestedFor(urlPathEqualTo(STOCK_URL))
                .withQueryParam("quantity", equalTo("2"))
                .withHeader("Idempotency-Key", equalTo("order-1-product-1")));

    }

    /**
     * Business error (409, e.g. insufficient stock). Feign throws Conflict, a FeignClientException (4xx), which
     * retry and the breaker are configured to ignore.
     */
    @Test
    void decreaseStock_throwsConflict_when409() {
        productMock.stubFor(put(urlPathEqualTo(STOCK_URL))
                .willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> productClient.decreaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(FeignException.Conflict.class);
    }

    /**
     * Server error (500) when decreasing stock: translated to FeignServerException, which does count as a failure of
     * the remote service.
     */
    @Test
    void decreaseStock_throwsServerException_when500() {
        productMock.stubFor(put(urlPathEqualTo(STOCK_URL))
                .willReturn(serverError()));

        assertThatThrownBy(() -> productClient.decreaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(FeignException.FeignServerException.class);
    }

    /**
     * Timeout when decreasing stock: a 600 ms response against a 300 ms readTimeout. This is the scenario that
     * motivates the idempotency key, because it is unknown whether product applied the decrease.
     */
    @Test
    void decreaseStock_throwsRetryableException_whenTimeout() {
        productMock.stubFor(put(urlPathEqualTo(STOCK_URL))
                .willReturn(ok().withFixedDelay(600)));

        assertThatThrownBy(() -> productClient.decreaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(RetryableException.class);
    }

    /**
     * 404 on a void method: dismiss404 does not apply because there is no return value to replace with "empty", so
     * the 404 arrives as FeignException.NotFound.
     */
    @Test
    void decreaseStock_when404() {
        productMock.stubFor(put(urlPathEqualTo(STOCK_URL))
                .willReturn(notFound()));

        assertThatThrownBy(() -> productClient.decreaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(FeignException.NotFound.class);
    }

    // Increase Stock (saga compensation)

    private static final String RESTORE_URL = "/api/v1/products/1/stock/restore";

    /**
     * Verifies the compensation request: PUT to the /stock/restore path, with the quantity query param and the
     * Idempotency-Key header (the SAME key the decrease used). exactly(1) ensures Feign did not retry on its own.
     */
    @Test
    void increaseStock_sendsQuantityAndIdempotencyKey() {

        productMock.stubFor(put(urlPathEqualTo(RESTORE_URL))
                .willReturn(noContent()));

        productClient.increaseStock(1L, 2, "order-1-product-1");

        productMock.verify(exactly(1), putRequestedFor(urlPathEqualTo(RESTORE_URL))
                .withQueryParam("quantity", equalTo("2"))
                .withHeader("Idempotency-Key", equalTo("order-1-product-1")));
    }

    /**
     * Server error (500) when restoring stock: translated to FeignServerException, which the saga treats as a failed
     * compensation.
     */
    @Test
    void increaseStock_throwsServerException_when500() {
        productMock.stubFor(put(urlPathEqualTo(RESTORE_URL))
                .willReturn(serverError()));

        assertThatThrownBy(() -> productClient.increaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(FeignException.FeignServerException.class);
    }

    /**
     * Timeout when restoring stock: a 600 ms response against a 300 ms readTimeout. Retrying is safe because the
     * restore is idempotent with the same key.
     */
    @Test
    void increaseStock_throwsRetryableException_whenTimeout() {
        productMock.stubFor(put(urlPathEqualTo(RESTORE_URL))
                .willReturn(noContent().withFixedDelay(600)));

        assertThatThrownBy(() -> productClient.increaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(RetryableException.class);
    }

    /**
     * 404 when restoring (unknown product): on a void method it arrives as FeignException.NotFound, a 4xx that retry
     * and the breaker ignore.
     */
    @Test
    void increaseStock_when404() {
        productMock.stubFor(put(urlPathEqualTo(RESTORE_URL))
                .willReturn(notFound()));

        assertThatThrownBy(() -> productClient.increaseStock(1L, 2, "order-1-product-1"))
                .isInstanceOf(FeignException.NotFound.class);
    }

}
