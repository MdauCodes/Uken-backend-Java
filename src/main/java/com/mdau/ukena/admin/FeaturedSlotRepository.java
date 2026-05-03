package com.mdau.ukena.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeaturedSlotRepository extends JpaRepository<FeaturedSlot, Integer> {
    List<FeaturedSlot> findAllByOrderByPositionAsc();
}