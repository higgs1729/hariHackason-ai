package com.hanamizuki.backend.api.decoration;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hanamizuki.backend.common.ETags;
import com.hanamizuki.backend.api.decoration.DecorationDtos.DecorationPutRequest;
import com.hanamizuki.backend.api.decoration.DecorationDtos.DecorationVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.DecorationService;

/** Screen 04. Addressed by {@code albumPhotoId}, never by the photo's own id. */
@RestController
@RequestMapping("/api/albums/{albumId}/photos/{albumPhotoId}")
public class DecorationController {

    private final DecorationService decorationService;

    public DecorationController(DecorationService decorationService) {
        this.decorationService = decorationService;
    }

    @GetMapping("/decoration")
    public ResponseEntity<DecorationVo> get(@PathVariable Long albumId,
                                            @PathVariable Long albumPhotoId,
                                            @AuthenticationPrincipal AuthUser principal) {
        DecorationVo vo = decorationService.get(albumId, albumPhotoId, principal.userId());
        return withETag(vo);
    }

    @PutMapping("/decoration")
    public ResponseEntity<DecorationVo> put(@PathVariable Long albumId,
                                            @PathVariable Long albumPhotoId,
                                            @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                            @RequestBody DecorationPutRequest request,
                                            @AuthenticationPrincipal AuthUser principal) {
        DecorationVo vo = decorationService.replace(albumId, albumPhotoId, principal.userId(),
                request.elements(), ETags.parse(ifMatch));
        return withETag(vo);
    }

    /** Best effort: the element list is already saved, this only refreshes a cache. */
    @PostMapping("/decoration/rendered")
    public ResponseEntity<Void> rendered(@PathVariable Long albumId,
                                         @PathVariable Long albumPhotoId,
                                         @RequestPart(value = "overlay", required = false) MultipartFile overlay,
                                         @RequestPart(value = "composite", required = false) MultipartFile composite,
                                         @AuthenticationPrincipal AuthUser principal) {
        decorationService.saveRendered(albumId, albumPhotoId, principal.userId(),
                bytes(overlay), bytes(composite));
        return ResponseEntity.noContent().build();
    }

    /**
     * Public on purpose — the share page and the OG collage fetch this without
     * a token. The URL carries no guessable secret beyond the ids, which is why
     * the share page itself is reached through a random token instead.
     */
    @GetMapping("/composite")
    public ResponseEntity<byte[]> composite(@PathVariable Long albumId,
                                            @PathVariable Long albumPhotoId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .body(decorationService.composite(albumId, albumPhotoId));
    }

    private static ResponseEntity<DecorationVo> withETag(DecorationVo vo) {
        return ResponseEntity.ok().eTag(ETags.of(vo.version())).body(vo);
    }


    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
