package com.mdau.ukena.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** cloneFromCatalogueIds is optional — when given, the new catalogue starts out
 *  seeded with a fresh, independent copy of those catalogues' items (merged,
 *  last one wins on a shared product) instead of starting empty. */
public record CreateCatalogueRequest(
        @NotBlank @Size(max = 160) String name,
        List<UUID> cloneFromCatalogueIds
) {}
