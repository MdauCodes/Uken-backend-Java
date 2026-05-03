package com.mdau.ukena.creator.dto;

import java.util.List;

public record CreatorDetail(
        String id,
        String firstName,
        String fullName,
        String craft,
        String region,
        String hook,
        String image,
        String portraitImage,
        String headerImage,
        List<String> storyParagraphs,
        String pullQuote,
        List<ProcessStep> processSteps,
        List<String> culturalMeaning,
        MapPin mapPin
) {}