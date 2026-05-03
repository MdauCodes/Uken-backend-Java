package com.mdau.ukena.product;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(String productId);
    Optional<ProductImage> findByProductIdAndIsPrimaryTrue(String productId);
    void deleteByProductId(String productId);
}