package com.mdau.ukena.creator.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatorProfileUpdate(
        @Size(min = 2, max = 80) String firstName,
        @Size(min = 2, max = 120) String fullName,
        @Size(max = 120) String region,
        @Size(max = 120) String craft,
        List<String> storyParagraphs,
        String pullQuote,
        String portraitImage,
        String headerImage
) {}