package com.mdau.ukena.promo.dto;

/** Lets the storefront fade promo/coupon UI on or off without ever exposing
 *  actual codes — just whether something is currently usable. */
public record PromoCodeStatusResponse(boolean anyActive, boolean welcomeActive) {}
