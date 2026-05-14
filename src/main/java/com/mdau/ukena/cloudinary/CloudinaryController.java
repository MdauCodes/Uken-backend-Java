package com.mdau.ukena.cloudinary;

import com.mdau.ukena.cloudinary.dto.SignRequest;
import com.mdau.ukena.cloudinary.dto.SignResponse;
import com.mdau.ukena.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/cloudinary")
@RequiredArgsConstructor
public class CloudinaryController {

    private final CloudinaryService cloudinaryService;

    // Sign upload â€” creator or admin gets a signed URL to upload directly
    // from the frontend (avoids routing the file through our server)
    @PostMapping("/sign")
    @PreAuthorize("hasAnyRole('CREATOR','ADMIN','ROLE_SUPPORT')")
    public ResponseEntity<ApiResponse<SignResponse>> sign(
            @Valid @RequestBody SignRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                cloudinaryService.signUpload(request)));
    }

    // Server-side upload â€” for cases where frontend sends file to us
    @PostMapping(value = "/upload",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CloudinaryService.UploadResult>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "ukena/uploads") String folder) {
        return ResponseEntity.ok(ApiResponse.ok(
                cloudinaryService.upload(file, folder)));
    }

    // Delete by publicId â€” admin or creator (frontend passes publicId)
    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAnyRole('CREATOR','ADMIN')")
    public ResponseEntity<ApiResponse<String>> delete(
            @PathVariable String publicId) {
        boolean deleted = cloudinaryService.deleteImage(publicId);
        return ResponseEntity.ok(deleted
                ? ApiResponse.ok("Image deleted successfully")
                : ApiResponse.fail("Image could not be deleted or not found"));
    }
}