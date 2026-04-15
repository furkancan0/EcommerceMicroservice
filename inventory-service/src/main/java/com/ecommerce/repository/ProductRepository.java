package com.ecommerce.repository;

import com.ecommerce.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    /** Find all active products in a given category — explicit JPQL, no naming ambiguity */
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.isActive = true")
    List<Product> findByCategoryActive(@Param("category") String category);

    /** Find all active products */
    @Query("SELECT p FROM Product p WHERE p.isActive = true")
    List<Product> findAllActive();

    /** Find by ID only if active */
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findActiveById(@Param("id") UUID id);
}