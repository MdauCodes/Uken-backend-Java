package com.mdau.ukena.media;

import com.mdau.ukena.common.ApiException;
import com.mdau.ukena.media.dto.UploadedMediaDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class MediaService {

    private static final List<String> ALLOWED_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 8L * 1024 * 1024; // 8MB

    @Value("${ukena.media.cloudinary-url:}")
    private String cloudinaryUrl;

    public UploadedMediaDto upload(MultipartFile file) {
        validateFile(file);

        // If Cloudinary URL is configured, upload to Cloudinary
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            return uploadToCloudinary(file);
        }

        // Dev fallback — return a placeholder URL
        String fileId = UUID.randomUUID().toString();
        log.warn("Cloudinary not configured — returning placeholder URL for file: {}",
                file.getOriginalFilename());
        return new UploadedMediaDto(
                "https://placehold.co/800x600?text=Image+" + fileId,
                fileId);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file provided");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest(
                    "Invalid file type. Allowed: JPEG, PNG, WebP");
        }
        if (file.getSize() > MAX_SIZE) {
            throw ApiException.badRequest("File size exceeds 8MB limit");
        }
    }

    private UploadedMediaDto uploadToCloudinary(MultipartFile file) {
        // TODO: Replace with Cloudinary Java SDK when added to pom.xml
        // For now, uses Cloudinary REST API directly
        try {
            // Parse cloudinary://key:secret@cloud-name
            String stripped = cloudinaryUrl.replace("cloudinary://", "");
            String[] parts = stripped.split("@");
            String cloudName = parts[1];
            String[] credentials = parts[0].split(":");
            String apiKey = credentials[0];
            String apiSecret = credentials[1];

            byte[] fileBytes = file.getBytes();
            String base64File = Base64.getEncoder().encodeToString(fileBytes);
            String fileId = UUID.randomUUID().toString().replace("-", "");

            String boundary = "----FormBoundary" + fileId;
            String body = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"\r\n\r\n"
                    + "data:" + file.getContentType() + ";base64," + base64File + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"public_id\"\r\n\r\n"
                    + fileId + "\r\n"
                    + "--" + boundary + "--\r\n";

            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
            String auth = Base64.getEncoder().encodeToString(
                    (apiKey + ":" + apiSecret).getBytes());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                String url = extractJsonField(responseBody, "secure_url");
                String publicId = extractJsonField(responseBody, "public_id");
                return new UploadedMediaDto(url, publicId);
            } else {
                log.error("Cloudinary upload failed: {}", response.body());
                throw ApiException.badRequest("Image upload failed");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload error", e);
            throw ApiException.badRequest("Image upload failed: " + e.getMessage());
        }
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }
}