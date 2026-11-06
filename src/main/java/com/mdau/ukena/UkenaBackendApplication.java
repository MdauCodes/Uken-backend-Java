package com.mdau.ukena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UkenaBackendApplication {
    public static void main(String[] args) {
        // Run on UTC wherever the server is (a Kenyan laptop, a UK or US host), so
        // nothing depends on the machine's clock zone. Moments are stored and sent
        // as UTC instants and shown in each viewer's own zone by the frontend;
        // UK-specific things (the market stall) name Europe/London explicitly.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(UkenaBackendApplication.class, args);
    }
}