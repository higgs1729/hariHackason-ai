package com.hanamizuki.backend.api.album;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.AlbumService;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    /**
     * The ETag is the album's version. Writes send it back as {@code If-Match},
     * and a stale one is rejected with 409 rather than quietly overwriting a
     * caption another member just changed.
     */
    @GetMapping("/{albumId}")
    public ResponseEntity<AlbumVo> get(@PathVariable Long albumId,
                                       @AuthenticationPrincipal AuthUser principal) {
        AlbumVo album = albumService.get(albumId, principal.userId());
        return ResponseEntity.ok()
                .eTag("\"" + album.version() + "\"")
                .body(album);
    }
}
