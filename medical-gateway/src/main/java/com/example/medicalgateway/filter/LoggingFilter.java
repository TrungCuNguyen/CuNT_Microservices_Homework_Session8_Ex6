package com.example.medicalgateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String ipAddress = (remoteAddress != null && remoteAddress.getAddress() != null)
                ? remoteAddress.getAddress().getHostAddress()
                : "UNKNOWN";

        String path = exchange.getRequest().getPath().toString();
        String method = exchange.getRequest().getMethod().toString();

        System.out.println(">>> [Medical Gateway Log] Client IP: " + ipAddress +
                " | Method: " + method + " | Path: " + path);

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

