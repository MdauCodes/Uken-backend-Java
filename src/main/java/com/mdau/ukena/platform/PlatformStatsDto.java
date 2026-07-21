package com.mdau.ukena.platform;

/** Live, real platform counts — used to back the storefront's trust messaging
 *  instead of hardcoded copy that can drift from the truth as the business grows. */
public record PlatformStatsDto(
        long makerCount,
        long productCount,
        long countryCount) {}
