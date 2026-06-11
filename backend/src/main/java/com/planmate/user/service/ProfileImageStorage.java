package com.planmate.user.service;

import com.planmate.user.exception.InvalidProfileImageException;
import com.planmate.user.exception.ProfileImageSaveFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProfileImageStorage {

    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final Path uploadDirectory;
    private final String publicPath;
    private final long maxSize;

    public ProfileImageStorage(
            @Value("${app.profile-image.upload-dir:uploads/profile-images}") String uploadDirectory,
            @Value("${app.profile-image.public-path:/uploads/profile-images}") String publicPath,
            @Value("${app.profile-image.max-size:2097152}") long maxSize
    ) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.publicPath = normalizePublicPath(publicPath);
        this.maxSize = maxSize;
    }

    public String store(MultipartFile image) {
        validate(image);

        try {
            Files.createDirectories(uploadDirectory);
            String filename = UUID.randomUUID() + extensionOf(image.getContentType());
            Path target = uploadDirectory.resolve(filename).normalize();
            if (!target.getParent().equals(uploadDirectory)) {
                throw new InvalidProfileImageException();
            }

            image.transferTo(target);
            return publicPath + "/" + filename;
        } catch (IOException exception) {
            throw new ProfileImageSaveFailedException(exception);
        }
    }

    public void deleteByUrl(String profileImageUrl) {
        if (profileImageUrl == null || !profileImageUrl.startsWith(publicPath + "/")) {
            return;
        }

        String filename = profileImageUrl.substring((publicPath + "/").length());
        Path target = uploadDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(uploadDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Losing the DB reference is more important than failing because of stale local files.
        }
    }

    public Path uploadDirectory() {
        return uploadDirectory;
    }

    public String publicPathPattern() {
        return publicPath + "/**";
    }

    private void validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidProfileImageException();
        }
        if (image.getSize() > maxSize) {
            throw new InvalidProfileImageException();
        }
        if (!EXTENSIONS_BY_CONTENT_TYPE.containsKey(image.getContentType())) {
            throw new InvalidProfileImageException();
        }
    }

    private String extensionOf(String contentType) {
        String extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new InvalidProfileImageException();
        }
        return extension;
    }

    private String normalizePublicPath(String value) {
        String normalized = value == null || value.isBlank() ? "/uploads/profile-images" : value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
