package com.ecommerce.filter;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtParser jwtParser;

    public JwtAuthFilter(@Value("${jwt.public-key-path}") String publicKeyPath) throws Exception {
        super(Config.class);
        PublicKey publicKey = loadPublicKey(publicKeyPath);
        this.jwtParser = Jwts.parser()
                .verifyWith(publicKey)
                .build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing Authorization header");
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid Authorization header format");
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = jwtParser
                        .parseSignedClaims(token)
                        .getPayload();

                // Enrich request with user context headers for downstream services
                ServerHttpRequest enrichedRequest = request.mutate()
                        .header("X-User-Id",    claims.getSubject())
                        .header("X-User-Email", claims.get("email", String.class))
                        .header("X-User-Role",  claims.get("role", String.class))
                        .header("X-JWT-Id",     claims.getId())
                        .build();

                return chain.filter(exchange.mutate().request(enrichedRequest).build());

            } catch (ExpiredJwtException e) {
                log.warn("JWT expired for request: {}", request.getPath());
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Token has expired");
            } catch (JwtException e) {
                log.warn("Invalid JWT: {}", e.getMessage());
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid token");
            }
        };
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes();
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        String key = Files.readString(Path.of(path))
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }

    @Override
    public List<String> shortcutFieldOrder() {
        return List.of();
    }

    public static class Config {}
}
