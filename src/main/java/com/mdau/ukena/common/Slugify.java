package com.mdau.ukena.common;

public final class Slugify {
    private Slugify() {}

    public static String slugify(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
