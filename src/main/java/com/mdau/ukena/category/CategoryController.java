package com.mdau.ukena.category;

import com.mdau.ukena.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // Public — nav/shop pulls real, active categories instead of a hardcoded list
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> listActive() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listActive()));
    }

    // Admin — full list including inactive
    @GetMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.listAll()));
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryDto>> create(@Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(categoryService.create(req), "Category created"));
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryDto>> update(
            @PathVariable String id, @Valid @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(id, req), "Category updated"));
    }

    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
