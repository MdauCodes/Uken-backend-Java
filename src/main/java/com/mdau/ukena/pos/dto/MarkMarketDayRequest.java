package com.mdau.ukena.pos.dto;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Both fields optional — omit both for the original bare "just flag this date"
 *  behaviour; set catalogueId to also restrict the till on that date. */
public record MarkMarketDayRequest(
        @Size(max = 160) String name,
        UUID catalogueId
) {}
