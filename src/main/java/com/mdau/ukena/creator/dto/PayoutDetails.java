package com.mdau.ukena.creator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Where a maker's earnings are paid. Only the maker (and admins processing
 * payouts) ever see this — it is never part of the public creator profile.
 *
 * @param method      MPESA or BANK
 * @param account     M-Pesa number or paybill, or the bank account number
 * @param accountName name registered on that M-Pesa line or bank account
 * @param bankName    bank name (BANK only)
 */
public record PayoutDetails(
        @NotBlank @Pattern(regexp = "MPESA|BANK") String method,
        @NotBlank @Size(min = 5, max = 60) String account,
        @NotBlank @Size(max = 120) String accountName,
        @Size(max = 120) String bankName) {}
