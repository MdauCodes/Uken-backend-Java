package com.mdau.ukena.category;

import jakarta.validation.constraints.*;
import java.util.List;

public record CategoryRequest(
        /** Slug used as the primary key and in shop URLs — e.g. "farm-produce".
         *  Ignored on update (the path {id} is authoritative there). */
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[a-z0-9-]+$",
                message = "must be lowercase letters, numbers and hyphens only")
        String id,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 40) String colorToken,
        int sortOrder,
        boolean active,
        List<String> craftValues
) {}
