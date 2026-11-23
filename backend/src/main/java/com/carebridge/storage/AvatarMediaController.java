package com.carebridge.storage;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/media/avatars")
public class AvatarMediaController {

    private final AvatarStorageService avatarStorageService;

    public AvatarMediaController(AvatarStorageService avatarStorageService) {
        this.avatarStorageService = avatarStorageService;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serveAvatar(@PathVariable String filename) {
        AvatarStorageService.LoadedAvatar loaded = avatarStorageService.loadAvatar(filename);
        return ResponseEntity.ok()
                .contentType(loaded.mediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(loaded.resource());
    }
}
