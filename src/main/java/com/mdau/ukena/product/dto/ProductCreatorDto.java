package com.mdau.ukena.product.dto;

public record ProductCreatorDto(
        String id,
        String firstName,
        String fullName,
        String region,
        String craft,
        String portrait,
        /** True when the creator account itself is suspended (deletedAt set) —
         *  independent of this product's own status. A product can be ACTIVE
         *  while its creator is suspended (e.g. products were individually
         *  reinstated without unsuspending the creator) — admin UI uses this
         *  to warn against exactly that mismatch. */
        boolean creatorSuspended
) {}