package com.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Responsibilities:
 *  - JWT validation via RSA public key
 *  - Rate limiting — Redis token-bucket per user/IP
 *  - Routing to downstream microservices
 *  - Circuit breaker fallbacks (Resilience4j)
 *  - Request enrichment (X-User-Id, X-User-Email, X-User-Role headers)
 *
 * Downstream services trust injected headers
 */
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
