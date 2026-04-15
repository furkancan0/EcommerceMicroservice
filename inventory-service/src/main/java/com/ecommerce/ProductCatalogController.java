package com.ecommerce;

import com.ecommerce.cache.InventoryCacheService;
import com.ecommerce.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
class ProductCatalogController {

    private final InventoryCacheService cacheService;

    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProduct(@PathVariable("productId") UUID productId) {
        return cacheService.getProduct(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Product>> listProducts(
            @RequestParam(name = "category", required = false) String category) {

        List<Product> products = category != null
                ? cacheService.getProductsByCategory(category)
                : List.of();

        return ResponseEntity.ok(products);
    }


    @GetMapping("/{productId}/availability")
    public ResponseEntity<Map<String, Object>> getAvailability(@PathVariable("productId") UUID productId) {
        int count = cacheService.getApproximateAvailableCount(productId);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "available", count,
                "note", "Approximate display value — actual reservation uses DB lock"
        ));
    }
}
