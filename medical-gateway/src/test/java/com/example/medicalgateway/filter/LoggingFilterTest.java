package com.example.medicalgateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingFilterTest {

    @Test
    void testFilterLogsRequestDetails() {
        LoggingFilter filter = new LoggingFilter();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/patients/1")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            filter.filter(exchange, chain).block();
            String output = outContent.toString();

            assertTrue(output.contains(">>> [Medical Gateway Log] Client IP: 127.0.0.1"));
            assertTrue(output.contains("Method: GET"));
            assertTrue(output.contains("Path: /api/v1/patients/1"));
        } finally {
            System.setOut(originalOut);
        }
    }
}

