package com.mdau.ukena.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<ArtisanApplication, String> {
    List<ArtisanApplication> findAllByOrderBySubmittedAtDesc();
    List<ArtisanApplication> findByStatusOrderBySubmittedAtDesc(ApplicationStatus status);
    boolean existsByEmail(String email);
}