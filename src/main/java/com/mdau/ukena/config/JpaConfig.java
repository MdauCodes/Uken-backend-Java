package com.mdau.ukena.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.mdau.ukena")
public class JpaConfig {
    // Connection pooling is handled by HikariCP (Spring Boot default).
    // Pool settings are configured in application.yaml.
    // This class enables explicit transaction management and
    // ensures all repositories under com.mdau.ukena are scanned.
}