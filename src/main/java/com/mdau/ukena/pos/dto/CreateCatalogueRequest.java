package com.mdau.ukena.pos.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/** cloneFromCatalogueIds is optional — when given, the new catalogue starts out
 *  seeded with a fresh, independent copy of those catalogues' items (merged,
 *  last one wins on a shared product) instead of starting empty. */
public record CreateCatalogueRequest(
        @NotBlank String name,
        List<UUID> cloneFromCatalogueIds
) {}
