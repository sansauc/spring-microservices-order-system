package com.sansa.service_order.service;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

// @DirtiesContext: the mocks are static with a dynamic port and JUnit restarts them (with a NEW port) for each test class,
// but Spring caches the context between classes and would keep the first class's port. That made every client
// test class except the first fail with "Connection refused". By discarding the context after each class,
// the next one starts a new context with the current ports.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = "spring.cloud.config.enabled=false")
@ActiveProfiles("test")
public abstract class AbstractWireMockTest {

    @RegisterExtension
    protected static WireMockExtension productMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    protected static WireMockExtension customerMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void registerServices(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.service-product[0].uri",
                productMock::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.service-customer[0].uri",
                customerMock::baseUrl);
    }

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

}
