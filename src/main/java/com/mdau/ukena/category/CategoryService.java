package com.mdau.ukena.category;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdau.ukena.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CategoryDto> listActive() {
        return repository.findByActiveTrueOrderBySortOrderAscNameAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc()
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public CategoryDto create(CategoryRequest req) {
        if (repository.existsById(req.id())) {
            throw ApiException.conflict("A category with id \"" + req.id() + "\" already exists");
        }
        Category category = Category.builder()
                .id(req.id().trim())
                .name(req.name().trim())
                .colorToken(req.colorToken().trim())
                .sortOrder(req.sortOrder())
                .active(req.active())
                .craftValues(toJson(req.craftValues()))
                .thumbnailImage(blankToNull(req.thumbnailImage()))
                .build();
        return toDto(repository.save(category));
    }

    @Transactional
    public CategoryDto update(String id, CategoryRequest req) {
        Category category = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        category.setName(req.name().trim());
        category.setColorToken(req.colorToken().trim());
        category.setSortOrder(req.sortOrder());
        category.setActive(req.active());
        category.setCraftValues(toJson(req.craftValues()));
        category.setThumbnailImage(blankToNull(req.thumbnailImage()));
        return toDto(repository.save(category));
    }

    @Transactional
    public void delete(String id) {
        Category category = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        category.setActive(false);
        repository.save(category);
    }

    CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getName(), c.getColorToken(), c.getSortOrder(),
                c.isActive(), parseList(c.getCraftValues()), c.getThumbnailImage());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private String toJson(List<String> values) {
        try { return objectMapper.writeValueAsString(values == null ? List.of() : values); }
        catch (Exception e) { return "[]"; }
    }
}
