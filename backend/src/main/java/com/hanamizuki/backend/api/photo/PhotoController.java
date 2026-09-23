package com.hanamizuki.backend.api.photo;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hanamizuki.backend.api.photo.PhotoVos.PhotoVo;
import com.hanamizuki.backend.api.photo.PhotoVos.UploadResultVo;
import com.hanamizuki.backend.common.PageVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.PhotoService;

/**
 * Uploading and serving photos.
 *
 * <p>Bytes go through here rather than a static handler on the storage
 * directory: a static mapping would make every photo readable by anyone who
 * can guess a path, with no album membership in the way.
 */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResultVo upload(@RequestParam("files") List<MultipartFile> files,
                                 @AuthenticationPrincipal AuthUser principal) {
        return photoService.upload(files, principal.userId());
    }

    /** {@code unassigned=true} is the picker on screen 03: photos in no album yet. */
    @GetMapping
    public PageVo<PhotoVo> list(@RequestParam(defaultValue = "false") boolean unassigned,
                                @RequestParam(defaultValue = "50") int limit,
                                @AuthenticationPrincipal AuthUser principal) {
        return photoService.list(principal.userId(), unassigned, Math.min(limit, 100));
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

    /** Content never changes once written, so it can be cached indefinitely. */
    private static ResponseEntity<byte[]> image(byte[] bytes) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(bytes);
    }
}
