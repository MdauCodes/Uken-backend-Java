package com.mdau.ukena.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewRepository extends JpaRepository<ReviewRecord, String> {
    List<ReviewRecord> findAllByOrderBySubmittedAtDesc();
    List<ReviewRecord> findByProductIdOrderBySubmittedAtDesc(String productId);
}