package com.mdau.ukena.saved;

import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/saved")
@PreAuthorize("hasRole('BUYER')")
@RequiredArgsConstructor
public class SavedItemController {

    private final SavedItemService savedItemService;

    @PostMapping("/{productId}")
    public ResponseEntity<ApiResponse<SavedItemDto>> save(
            @PathVariable String productId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        savedItemService.save(productId, currentUser),
                        "Product saved"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavedItemDto>>> list(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                savedItemService.listSaved(currentUser)));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> unsave(
            @PathVariable String productId,
            @AuthenticationPrincipal CurrentUser currentUser) {
        savedItemService.unsave(productId, currentUser);
        return ResponseEntity.noContent().build();
    }
}