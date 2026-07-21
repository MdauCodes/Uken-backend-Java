package com.mdau.ukena.shipping;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ShippingSettingsRepository extends JpaRepository<ShippingSettings, UUID> {
}
