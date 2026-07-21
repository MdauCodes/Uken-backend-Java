package com.mdau.ukena.email;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
}
