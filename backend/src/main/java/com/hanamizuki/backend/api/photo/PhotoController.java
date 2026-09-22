package com.hanamizuki.backend.api.photo;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.PhotoService;

/**
 * Serving image bytes.
 *
 * <p>Files are reached through this rather than a static handler on the storage
 * directory: a static mapping would make every photo readable by anyone who can
 * guess a path, with no album membership in the way.
 */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<byte[]> original(@PathVariable Long photoId,
                                           @AuthenticationPrincipal AuthUser principal) {
        return image(photoService.serve(photoId, principal.userId(), false));
    }

    @GetMapping("/{photoId}/thumb")
    public ResponseEntity<byte[]> thumb(@PathVariable Long photoId,
                                        @AuthenticationPrincipal AuthUser principal) {
        return image(photoService.serve(photoId, principal.userId(), true));
    }

    /** Content is immutable once written, so it can be cached indefinitely. */
    private static ResponseEntity<byte[]> image(byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(bytes);
    }
}
