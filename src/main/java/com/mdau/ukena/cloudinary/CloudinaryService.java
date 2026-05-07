package com.mdau.ukena.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.mdau.ukena.cloudinary.dto.SignRequest;
import com.mdau.ukena.cloudinary.dto.SignResponse;
import com.mdau.ukena.common.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 8L * 1024 * 1024;

    @Value("${ukena.cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${ukena.cloudinary.api-key:}")
    private String apiKey;

    // ── Sign upload request (frontend uploads directly to Cloudinary) ─────

    public SignResponse signUpload(SignRequest request) {
        try {
            long timestamp = System.currentTimeMillis() / 1000L;

            Map<String, Object> params = new HashMap<>();
            params.put("timestamp", timestamp);
            params.put("folder", request.folder());
            if (request.publicId() != null && !request.publicId().isBlank()) {
                params.put("public_id", request.publicId());
            }

            String signature = cloudinary.apiSignRequest(params,
                    cloudinary.config.apiSecret);

            log.info("Cloudinary upload signed for folder: {}", request.folder());

            return new SignResponse(
                    signature, timestamp, apiKey, cloudName,
                    request.folder(), request.publicId(),
                    "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload");

        } catch (Exception e) {
            log.error("Failed to sign Cloudinary upload: {}", e.getMessage(), e);
            throw ApiException.badRequest("Failed to sign upload: " + e.getMessage());
        }
    }

    // ── Server-side upload (used for media/upload endpoint) ───────────────

    public UploadResult upload(MultipartFile file, String folder) {
        validateFile(file);
        try {
            String publicId = folder + "/" + UUID.randomUUID().toString().replace("-", "");

            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "public_id", publicId,
                            "folder", folder,
                            "resource_type", "image",
                            "overwrite", false));

            String url = (String) result.get("secure_url");
            String uploadedPublicId = (String) result.get("public_id");

            log.info("Cloudinary upload success: {}", uploadedPublicId);
            return new UploadResult(url, uploadedPublicId);

        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw ApiException.badRequest("Image upload failed: " + e.getMessage());
        }
    }

    // ── Delete single image ───────────────────────────────────────────────

    public boolean deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank()) return false;
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId,
                    ObjectUtils.emptyMap());
            String outcome = String.valueOf(result.get("result"));
            if ("ok".equals(outcome)) {
                log.info("Cloudinary image deleted: {}", publicId);
                return true;
            }
            log.warn("Cloudinary delete returned: {} for: {}", outcome, publicId);
            return false;
        } catch (Exception e) {
            log.error("Failed to delete Cloudinary image {}: {}", publicId, e.getMessage());
            return false;
        }
    }

    // ── Delete multiple images ────────────────────────────────────────────

    public void deleteImages(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) return;
        publicIds.forEach(this::deleteImage);
    }

    // ── Extract publicId from a Cloudinary URL ────────────────────────────

    public String extractPublicId(String cloudinaryUrl) {
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) return null;
        try {
            String path = cloudinaryUrl.substring(
                    cloudinaryUrl.indexOf("/image/upload/") + 14);
            if (path.matches("v\\d+/.*")) {
                path = path.substring(path.indexOf('/') + 1);
            }
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex > 0) path = path.substring(0, dotIndex);
            return path;
        } catch (Exception e) {
            log.warn("Could not extract publicId from URL: {}", cloudinaryUrl);
            return null;
        }
    }

    // ── Validation ────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw ApiException.badRequest("No file provided");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw ApiException.badRequest("Invalid file type. Allowed: JPEG, PNG, WebP");
        if (file.getSize() > MAX_SIZE)
            throw ApiException.badRequest("File size exceeds 8MB limit");
    }

    // ── Inner result record ───────────────────────────────────────────────

    public record UploadResult(String url, String publicId) {}
}