package com.sansa.service_order.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds the X-Served-By header with the service and port that handled the request,
 * so load balancing between instances can be verified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstanceInfoFilter extends OncePerRequestFilter {

    private final Environment environment;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String instance = environment.getProperty("spring.application.name") + ":" + environment.getProperty("local.server.port");
        response.setHeader("X-Served-By", instance);
        log.info("[{}] {} {}", instance, request.getMethod(), request.getRequestURI());
        filterChain.doFilter(request, response);
    }
}
