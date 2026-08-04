package com.mdau.ukena.email;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.email.dto.EmailTemplateDto;
import com.mdau.ukena.email.dto.EmailTemplateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository templateRepository;

    public List<EmailTemplateDto> list() {
        return templateRepository.findAll().stream().map(this::toDto).toList();
    }

    public EmailTemplateDto get(UUID id) {
        return toDto(findOrThrow(id));
    }

    public EmailTemplateDto create(EmailTemplateRequest req, UUID createdBy) {
        EmailTemplate saved = templateRepository.save(EmailTemplate.builder()
                .name(req.name())
                .htmlContent(req.htmlContent())
                .thumbnailUrl(req.thumbnailUrl())
                .category(req.category())
                .logoUrl(req.logoUrl())
                .accentColor(req.accentColor())
                .senderName(req.senderName())
                .ctaLabel(req.ctaLabel())
                .ctaUrl(req.ctaUrl())
                .createdBy(createdBy)
                .build());
        return toDto(saved);
    }

    public EmailTemplateDto update(UUID id, EmailTemplateRequest req) {
        EmailTemplate t = findOrThrow(id);
        t.setName(req.name());
        t.setHtmlContent(req.htmlContent());
        t.setThumbnailUrl(req.thumbnailUrl());
        t.setCategory(req.category());
        t.setLogoUrl(req.logoUrl());
        t.setAccentColor(req.accentColor());
        t.setSenderName(req.senderName());
        t.setCtaLabel(req.ctaLabel());
        t.setCtaUrl(req.ctaUrl());
        return toDto(templateRepository.save(t));
    }

    public void delete(UUID id) {
        if (!templateRepository.existsById(id)) throw ApiException.notFound("Template not found");
        templateRepository.deleteById(id);
    }

    private EmailTemplate findOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Template not found"));
    }

    private EmailTemplateDto toDto(EmailTemplate t) {
        return new EmailTemplateDto(t.getId(), t.getName(), t.getHtmlContent(),
                t.getThumbnailUrl(), t.getCategory(), t.getLogoUrl(), t.getAccentColor(),
                t.getSenderName(), t.getCtaLabel(), t.getCtaUrl(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
