package com.mdau.ukena.cloudinary;

import com.mdau.ukena.cloudinary.dto.DeleteImageRequest;
import com.mdau.ukena.cloudinary.dto.SignRequest;
import com.mdau.ukena.cloudinary.dto.SignResponse;
import com.mdau.ukena.common.ApiResponse;
import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cloudinary")
@RequiredArgsConstructor
public class CloudinaryController {

    private final CloudinaryService cloudinaryService;

    /**
     * Returns a signed upload token so the frontend can post directly to
     * Cloudinary without routing the binary through this server.
     * Restricted to authenticated users to prevent anonymous token farming.
     */
    @PostMapping("/sign")
    public ResponseEntity<ApiResponse<SignResponse>> sign(
            @Valid @RequestBody SignRequest request,
            @AuthenticationPrincipal CurrentUser currentUser) {
        // Unauthenticated callers (e.g. application form) may only upload to
        // the applications folder to prevent anonymous token farming.
        if (currentUser == null && !"applications".equals(request.folder())) {
            throw ApiException.forbidden("Unauthenticated uploads are only permitted to the applications folder");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                cloudinaryService.signUpload(request)));
    }

    /**
     * Deletes an image by its Cloudinary public_id.
     * Only CREATOR and ADMIN roles may call this.
     */
    @DeleteMapping("/image")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Boolean>> delete(
            @Valid @RequestBody DeleteImageRequest request) {
        boolean deleted = cloudinaryService.deleteImage(request.publicId());
        return ResponseEntity.ok(ApiResponse.ok(deleted));
    }
}