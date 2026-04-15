package com.ecommerce.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            // Fallback to IP if user not identified
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null) {
                return Mono.just("ip:" + remoteAddress.getAddress().getHostAddress());
            }
            return Mono.just("user:unknown");
        };
    }
}
