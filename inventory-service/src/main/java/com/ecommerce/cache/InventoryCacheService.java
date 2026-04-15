package com.ecommerce.cache;

import com.ecommerce.entity.Product;
import com.ecommerce.repository.InventoryRepository;
import com.ecommerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache-Aside pattern implementation.
 *
 *   Product catalog   → 10 min TTL
 *   Category listings → 5 min TTL
 *   Available count   → 30 sec TTL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCacheService {

    private final StringRedisTemplate  redisTemplate;
    private final ProductRepository    productRepo;
    private final InventoryRepository  inventoryRepo;
    private final ObjectMapper         objectMapper;

    private static final Duration PRODUCT_TTL   = Duration.ofMinutes(10);
    private static final Duration CATEGORY_TTL  = Duration.ofMinutes(5);
    private static final Duration INVENTORY_TTL = Duration.ofSeconds(30);

    // Product catalog
    public Optional<Product> getProduct(UUID productId) {
        String key = "product:" + productId;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return Optional.of(deserialize(cached, Product.class));
        }
        Optional<Product> product = productRepo.findById(productId);
        product.ifPresent(p -> cacheSet(key, p, PRODUCT_TTL));
        return product;
    }

    public List<Product> getProductsByCategory(String category) {
        String key = "products:category:" + category;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return deserializeList(cached, Product.class);
        }

        List<Product> products = productRepo.findByCategoryActive(category);
        cacheSet(key, products, CATEGORY_TTL);
        return products;
    }

    public int getApproximateAvailableCount(UUID productId) {
        String key = "inventory:available:" + productId;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            return Integer.parseInt(cached);
        }

        int available = inventoryRepo.findAvailableQuantityByProductId(productId).orElse(0);
        try {
            redisTemplate.opsForValue().set(key, String.valueOf(available), INVENTORY_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache inventory count for {}: {}", productId, e.getMessage());
        }
        return available;
    }

    // Cache eviction
    public void evictProductCache(UUID productId) {
        redisTemplate.delete("product:" + productId);
        log.debug("Evicted product cache: {}", productId);
    }

    public void evictInventoryCache(UUID productId) {
        redisTemplate.delete("inventory:available:" + productId);
        log.debug("Evicted inventory cache: {}", productId);
    }

    public void evictCategoryCache(String category) {
        redisTemplate.delete("products:category:" + category);
        log.debug("Evicted category cache: {}", category);
    }

    // Private helpers
    private void cacheSet(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for key {}: {}", key, e.getMessage());
            // Non-fatal — application continues without caching
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Cache deserialization failed — will reload from DB: {}", e.getMessage());
            throw new RuntimeException("Cache deserialization failed", e);
        }
    }

    private <T> List<T> deserializeList(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.warn("Cache list deserialization failed: {}", e.getMessage());
            throw new RuntimeException("Cache deserialization failed", e);
        }
    }
}
