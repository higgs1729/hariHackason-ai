package com.hanamizuki.backend.api.share;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hanamizuki.backend.api.share.ShareVos.ShareLinkVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.ShareService;

/** Minting and revoking the public link. The link itself is served elsewhere. */
@RestController
@RequestMapping("/api/albums/{albumId}/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    public ShareLinkVo share(@PathVariable Long albumId,
                             @AuthenticationPrincipal AuthUser principal) {
        return shareService.share(albumId, principal.userId(), baseUrl());
    }

    @GetMapping
    public ShareLinkVo current(@PathVariable Long albumId,
                               @AuthenticationPrincipal AuthUser principal) {
        return shareService.current(albumId, principal.userId(), baseUrl());
    }

    @DeleteMapping
    public ResponseEntity<Void> revoke(@PathVariable Long albumId,
                                       @AuthenticationPrincipal AuthUser principal) {
        shareService.revoke(albumId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Built from the incoming request, never from configuration.
     *
     * <p>The demo is served through a tunnel whose hostname changes every time
     * it reconnects. A base URL in a properties file would mean every link
     * handed out before the reconnect stops working.
     */
    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
