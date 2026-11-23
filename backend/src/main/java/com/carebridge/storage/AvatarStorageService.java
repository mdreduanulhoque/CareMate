package com.carebridge.storage;

import com.carebridge.common.BadRequestException;
import com.carebridge.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores and serves caretaker profile avatars safely using magic-byte inspection,
 * random UUID filenames, strict size caps, and canonical path-traversal checks.
 */
@Service
public class AvatarStorageService {

    public static final String AVATAR_URL_PREFIX = "/api/media/avatars/";
    private static final long MAX_AVATAR_BYTES = 2L * 1024L * 1024L;
    private static final Pattern SAFE_FILENAME_PATTERN =
            Pattern.compile("^[0-9a-fA-F\\-]{36}\\.(jpg|png|webp)$");

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);

    private final Path avatarRootDir;

    public AvatarStorageService(@Value("${carebridge.storage.avatar-dir:uploads/avatars}") String avatarDir) {
        this.avatarRootDir = Paths.get(avatarDir).toAbsolutePath().normalize();
    }

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please choose an image file to upload.");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BadRequestException("Profile image must be 2 MB or smaller.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Could not read uploaded image file.");
        }

        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
            throw new BadRequestException("Profile image must be a non-empty file up to 2 MB.");
        }

        DetectedImageType imageType = detectByMagicBytes(bytes);
        if (imageType == null) {
            throw new BadRequestException("Only valid JPEG, PNG, or WebP images are allowed.");
        }

        String filename = UUID.randomUUID() + imageType.extension;
        try {
            Files.createDirectories(avatarRootDir);
            Path destination = resolveContainedPath(filename);
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return AVATAR_URL_PREFIX + filename;
        } catch (IOException ex) {
            log.error("Failed to store avatar image {}", filename, ex);
            throw new IllegalStateException("Could not store profile image right now.");
        }
    }

    public LoadedAvatar loadAvatar(String filename) {
        if (filename == null || !SAFE_FILENAME_PATTERN.matcher(filename).matches()) {
            throw new NotFoundException("Profile image not found.");
        }

        Path target = resolveContainedPath(filename);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new NotFoundException("Profile image not found.");
        }

        try {
            Resource resource = new UrlResource(target.toUri());
            MediaType mediaType = mediaTypeForFilename(filename);
            return new LoadedAvatar(resource, mediaType);
        } catch (IOException ex) {
            throw new NotFoundException("Profile image not found.");
        }
    }

    public void deleteIfManaged(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_URL_PREFIX)) {
            return;
        }
        String filename = avatarUrl.substring(AVATAR_URL_PREFIX.length());
        if (!SAFE_FILENAME_PATTERN.matcher(filename).matches()) {
            return;
        }
        try {
            Path target = resolveContainedPath(filename);
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.debug("Could not delete replaced avatar {}: {}", filename, ex.getMessage());
        }
    }

    private Path resolveContainedPath(String filename) {
        Path resolved = avatarRootDir.resolve(filename).normalize();
        if (!resolved.startsWith(avatarRootDir)) {
            throw new BadRequestException("Invalid avatar file path.");
        }
        return resolved;
    }

    static DetectedImageType detectByMagicBytes(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return DetectedImageType.JPEG;
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return DetectedImageType.PNG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return DetectedImageType.WEBP;
        }
        return null;
    }

    private MediaType mediaTypeForFilename(String filename) {
        if (filename.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (filename.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    enum DetectedImageType {
        JPEG(".jpg"),
        PNG(".png"),
        WEBP(".webp");

        final String extension;

        DetectedImageType(String extension) {
            this.extension = extension;
        }
    }

    public record LoadedAvatar(Resource resource, MediaType mediaType) {
    }
}
