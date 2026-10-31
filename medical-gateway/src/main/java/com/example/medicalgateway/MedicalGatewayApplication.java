package com.example.medicalgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MedicalGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalGatewayApplication.class, args);
    }

}

