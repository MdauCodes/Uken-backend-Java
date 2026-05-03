package com.mdau.ukena.product;

public enum ProductStatus {
    ACTIVE,                // visible and purchasable
    OUT_OF_STOCK,          // creator marked it — visible but not purchasable
    SUSPENDED_BY_CREATOR,  // creator hid it temporarily
    SUSPENDED_BY_ADMIN     // admin suspended — not visible to buyers
}