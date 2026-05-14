package com.mdau.ukena.delivery;

import com.mdau.ukena.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryZoneService {

    private final DeliveryZoneRepository repository;

    @Transactional(readOnly = true)
    public List<DeliveryZoneDto> listActive() {
        return repository.findByActiveTrueOrderByCountryAscSortOrderAscNameAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryZoneDto> listAll() {
        return repository.findAllByOrderByCountryAscSortOrderAscNameAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public DeliveryZoneDto create(DeliveryZoneRequest req) {
        DeliveryZone zone = DeliveryZone.builder()
                .name(req.name().trim())
                .country(req.country().trim())
                .shippingPence(req.shippingPence())
                .active(req.active())
                .sortOrder(req.sortOrder())
                .build();
        return toDto(repository.save(zone));
    }

    @Transactional
    public DeliveryZoneDto update(UUID id, DeliveryZoneRequest req) {
        DeliveryZone zone = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Delivery zone not found"));
        zone.setName(req.name().trim());
        zone.setCountry(req.country().trim());
        zone.setShippingPence(req.shippingPence());
        zone.setActive(req.active());
        zone.setSortOrder(req.sortOrder());
        return toDto(repository.save(zone));
    }

    @Transactional
    public void delete(UUID id) {
        DeliveryZone zone = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Delivery zone not found"));
        zone.setActive(false);
        repository.save(zone);
    }

    public DeliveryZone getActiveById(UUID id) {
        return repository.findById(id)
                .filter(DeliveryZone::isActive)
                .orElseThrow(() -> ApiException.badRequest(
                        "Invalid or unavailable delivery zone"));
    }

    private DeliveryZoneDto toDto(DeliveryZone z) {
        return new DeliveryZoneDto(z.getId(), z.getName(), z.getCountry(),
                z.getShippingPence(), z.isActive(), z.getSortOrder());
    }
}