package com.mdau.ukena.product;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nightly sweep — purges Cloudinary images for products soft-deleted more than
 *  7 days ago, ending their admin recovery window. See ProductService for the
 *  actual delete/restore/purge logic. */
@Component
@RequiredArgsConstructor
public class ProductPurgeScheduler {

    private final ProductService productService;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeExpiredDeletedProducts() {
        productService.purgeExpiredDeletedProducts();
    }
}
