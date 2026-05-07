package com.mdau.ukena.media;

import com.mdau.ukena.cloudinary.CloudinaryService;
import com.mdau.ukena.media.dto.UploadedMediaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final CloudinaryService cloudinaryService;

    public UploadedMediaDto upload(MultipartFile file) {
        CloudinaryService.UploadResult result =
                cloudinaryService.upload(file, "ukena/media");
        return new UploadedMediaDto(result.url(), result.publicId());
    }
}