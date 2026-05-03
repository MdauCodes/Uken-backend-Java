package com.mdau.ukena.product.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ProductCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(1) @Max(10_000_000) int pricePence,
        @NotBlank String heroImage,
        @NotBlank @Size(max = 4000) String pieceStory,
        @Size(max = 20) List<String> materials,
        @Size(max = 200) String dimensions,
        String care
) {}