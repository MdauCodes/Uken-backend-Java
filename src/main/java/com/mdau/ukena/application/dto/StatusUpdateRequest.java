package com.mdau.ukena.application.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
        @NotBlank String status,
        String notes,
        /** Required to be {@code true} when approving an application whose email
         *  already belongs to an existing non-creator account (e.g. a buyer) —
         *  makes the account-role change an explicit admin decision rather than
         *  a silent side effect. */
        Boolean confirmRoleChange
) {}