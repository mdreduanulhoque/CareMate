package com.carebridge.storage;

import com.carebridge.common.BadRequestException;
import com.carebridge.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarStorageServiceTest {

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private static final byte[] VALID_JPEG_BYTES = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10
    };

    private static final byte[] VALID_WEBP_BYTES = new byte[]{
            'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '
    };

    @Test
    void storesAndLoadsValidPngJpegAndWebpImages(@TempDir Path tempDir) {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());

        String pngUrl = service.storeAvatar(new MockMultipartFile("file", "nurse.png", "image/png", VALID_PNG_BYTES));
        assertThat(pngUrl).startsWith(AvatarStorageService.AVATAR_URL_PREFIX).endsWith(".png");
        AvatarStorageService.LoadedAvatar loadedPng =
                service.loadAvatar(pngUrl.substring(AvatarStorageService.AVATAR_URL_PREFIX.length()));
        assertThat(loadedPng.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(loadedPng.resource().exists()).isTrue();

        String jpgUrl = service.storeAvatar(new MockMultipartFile("file", "photo.jpeg", "image/jpeg", VALID_JPEG_BYTES));
        assertThat(jpgUrl).endsWith(".jpg");

        String webpUrl = service.storeAvatar(new MockMultipartFile("file", "pic.webp", "image/webp", VALID_WEBP_BYTES));
        assertThat(webpUrl).endsWith(".webp");

        service.deleteIfManaged(pngUrl);
        String deletedName = pngUrl.substring(AvatarStorageService.AVATAR_URL_PREFIX.length());
        assertThatThrownBy(() -> service.loadAvatar(deletedName)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsSpoofedSvgOrTextFileWithImageExtension(@TempDir Path tempDir) {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        byte[] svgPayload = "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8);

        MockMultipartFile spoofed = new MockMultipartFile("file", "evil.jpg", "image/jpeg", svgPayload);
        assertThatThrownBy(() -> service.storeAvatar(spoofed))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsOversizedFileOver2Mb(@TempDir Path tempDir) {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());
        byte[] oversized = new byte[2 * 1024 * 1024 + 10];
        System.arraycopy(VALID_PNG_BYTES, 0, oversized, 0, VALID_PNG_BYTES.length);

        MockMultipartFile large = new MockMultipartFile("file", "big.png", "image/png", oversized);
        assertThatThrownBy(() -> service.storeAvatar(large))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void blocksPathTraversalAndInvalidFilenames(@TempDir Path tempDir) {
        AvatarStorageService service = new AvatarStorageService(tempDir.toString());

        assertThatThrownBy(() -> service.loadAvatar("../application.properties"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.loadAvatar("..\\secret.txt"))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.loadAvatar("not-a-uuid.png"))
                .isInstanceOf(NotFoundException.class);
    }
}
