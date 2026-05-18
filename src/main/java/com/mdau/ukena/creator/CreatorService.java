package com.mdau.ukena.creator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.cloudinary.CloudinaryService;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.creator.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorService {

    private final CreatorRepository creatorRepository;
    private final ObjectMapper objectMapper;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<CreatorSummary> list(String craft, String region) {
        return creatorRepository.findFiltered(craft, region)
                .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CreatorDetail getById(String id) {
        Creator creator = creatorRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Creator not found"));
        return toDetail(creator);
    }

    @Transactional(readOnly = true)
    public CreatorDetail getByCreatorId(String creatorId) {
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        return toDetail(creator);
    }

    @Transactional
    public CreatorDetail updateProfile(String creatorId, CreatorProfileUpdate req) {
        Creator creator = creatorRepository.findById(creatorId)
                .orElseThrow(() -> ApiException.notFound("Creator profile not found"));
        if (req.firstName()       != null) creator.setFirstName(req.firstName());
        if (req.fullName()        != null) creator.setFullName(req.fullName());
        if (req.region()          != null) creator.setRegion(req.region());
        if (req.craft()           != null) creator.setCraft(req.craft());
        if (req.pullQuote()       != null) creator.setPullQuote(req.pullQuote());
        if (req.portraitImage() != null) {
            String oldPortrait = creator.getPortraitImage();
            creator.setPortraitImage(req.portraitImage());
            if (oldPortrait != null && !oldPortrait.equals(req.portraitImage()))
                cloudinaryService.deleteImage(cloudinaryService.extractPublicId(oldPortrait));
        }
        if (req.headerImage() != null) {
            String oldHeader = creator.getHeaderImage();
            creator.setHeaderImage(req.headerImage());
            if (oldHeader != null && !oldHeader.equals(req.headerImage()))
                cloudinaryService.deleteImage(cloudinaryService.extractPublicId(oldHeader));
        }
        if (req.storyParagraphs() != null)
            creator.setStoryParagraphs(toJson(req.storyParagraphs()));
        if (req.processSteps() != null) {
            // Delete removed workshop images from Cloudinary before overwriting.
            List<String> oldUrls = parseList(creator.getProcessSteps(),
                    new TypeReference<List<ProcessStep>>() {})
                    .stream().map(ProcessStep::image).toList();
            List<String> newUrls = req.processSteps().stream()
                    .map(CreatorProfileUpdate.ProcessStepUpdate::image).toList();
            oldUrls.stream()
                    .filter(url -> !newUrls.contains(url))
                    .map(cloudinaryService::extractPublicId)
                    .filter(pid -> pid != null)
                    .forEach(cloudinaryService::deleteImage);
            creator.setProcessSteps(toJson(req.processSteps()));
        }
        return toDetail(creatorRepository.save(creator));
    }

    CreatorSummary toSummary(Creator c) {
        return new CreatorSummary(c.getId(), c.getFirstName(), c.getFullName(),
                c.getCraft(), c.getRegion(), c.getHook(), c.getImage());
    }

    CreatorDetail toDetail(Creator c) {
        return new CreatorDetail(
                c.getId(), c.getFirstName(), c.getFullName(),
                c.getCraft(), c.getRegion(), c.getHook(), c.getImage(),
                c.getPortraitImage(), c.getHeaderImage(),
                parseList(c.getStoryParagraphs(), new TypeReference<>() {}),
                c.getPullQuote(),
                parseList(c.getProcessSteps(), new TypeReference<>() {}),
                parseList(c.getCulturalMeaning(), new TypeReference<>() {}),
                parseMapPin(c.getMapPin()));
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, ref); }
        catch (Exception e) { log.warn("Failed to parse JSON list: {}", e.getMessage()); return List.of(); }
    }

    private MapPin parseMapPin(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, MapPin.class); }
        catch (Exception e) { return null; }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }
}