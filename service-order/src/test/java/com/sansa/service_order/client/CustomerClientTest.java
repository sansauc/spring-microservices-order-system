package com.sansa.service_order.client;

import com.github.tomakehurst.wiremock.http.Fault;
import com.sansa.service_order.dto.CustomerResponse;
import com.sansa.service_order.service.AbstractWireMockTest;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CustomerClientTest extends AbstractWireMockTest {

    private static final String CUSTOMER_URL = "/api/v1/customers/1";

    @Autowired
    private CustomerClient customerClient;


    @Test
    void findCustomerById_returnsCustomer_when200(){

        customerMock.stubFor(get(urlPathEqualTo(CUSTOMER_URL))
                .willReturn(okJson("""
                        {"id": 1, "name": "Cliente", "email": "cliente_test@example.com"}
                        """)));

        Optional<CustomerResponse> result = customerClient.findCustomerById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Cliente");
        assertThat(result.get().email()).isEqualTo("cliente_test@example.com");

    }

    /**
     * The customer does not exist (404). With dismiss404 Feign does not throw and returns Optional.empty().
     */
    @Test
    void findCustomerById_returnsEmpty_when404(){

        customerMock.stubFor(get(urlPathEqualTo(CUSTOMER_URL))
                .willReturn(notFound()));

        Optional<CustomerResponse> result = customerClient.findCustomerById(1L);

        assertThat(result).isEmpty();
    }

    /**
     * customer responds 500: Feign throws FeignServerException (a server error, not a client one).
     */
    @Test
    void findCustomerById_throwsServerException_when500(){

        customerMock.stubFor(get(urlPathEqualTo(CUSTOMER_URL))
                .willReturn(serverError()));

        assertThatThrownBy(() -> customerClient.findCustomerById(1L))
                .isInstanceOf(FeignException.FeignServerException.class);
    }

    /**
     * customer takes longer than the read timeout (response delayed by 600 ms): Feign throws RetryableException.
     */
    @Test
    void findCustomerById_throwsRetryableException_whenTimeout(){

        customerMock.stubFor(get(urlPathEqualTo(CUSTOMER_URL))
                .willReturn(okJson("""
                        {"id": 1, "name": "Cliente", "email": "cliente_test@example.com"}
                        """).withFixedDelay(600)));

        assertThatThrownBy(() -> customerClient.findCustomerById(1L))
                .isInstanceOf(RetryableException.class);

    }

    /**
     * The connection is cut mid-response (connection reset): Feign throws RetryableException.
     */
    @Test
    void findCustomerById_throwsRetryableException_whenConnectionReset(){

        customerMock.stubFor(get(urlPathEqualTo(CUSTOMER_URL))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> customerClient.findCustomerById(1L))
                .isInstanceOf(RetryableException.class);
    }

}
