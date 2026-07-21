package com.mdau.ukena.shipping;

import com.mdau.ukena.shipping.dto.ShippingSettingsDto;
import com.mdau.ukena.shipping.dto.ShippingSettingsUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShippingSettingsService {

    private final ShippingSettingsRepository repository;

    /** There is always exactly one row — created with defaults on first access. */
    @Transactional
    public ShippingSettings getOrCreate() {
        return repository.findAll().stream().findFirst()
                .orElseGet(() -> repository.save(ShippingSettings.builder().build()));
    }

    @Transactional(readOnly = true)
    public ShippingSettingsDto get() {
        return toDto(getOrCreate());
    }

    @Transactional
    public ShippingSettingsDto update(ShippingSettingsUpdateRequest req) {
        ShippingSettings settings = getOrCreate();
        settings.setRatePencePerKg(req.ratePencePerKg());
        settings.setFallbackWeightGrams(req.fallbackWeightGrams());
        return toDto(repository.save(settings));
    }

    private ShippingSettingsDto toDto(ShippingSettings s) {
        return new ShippingSettingsDto(s.getRatePencePerKg(), s.getFallbackWeightGrams(), s.getUpdatedAt());
    }
}
