package com.mdau.ukena.product.dto;

public record GalleryImage(
        String src,
        String alt,
        String kind   // "product" or "process"
) {}