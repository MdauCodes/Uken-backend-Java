package com.mdau.ukena.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<ArtisanApplication, String> {
    List<ArtisanApplication> findAllByOrderBySubmittedAtDesc();

    java.util.Optional<ArtisanApplication> findFirstByUserIdOrderBySubmittedAtDesc(java.util.UUID userId);

    java.util.Optional<ArtisanApplication> findFirstByEmailIgnoreCaseOrderBySubmittedAtDesc(String email);
    List<ArtisanApplication> findByStatusOrderBySubmittedAtDesc(ApplicationStatus status);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
}