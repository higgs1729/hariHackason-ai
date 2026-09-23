package com.hanamizuki.backend.api.share;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.hanamizuki.backend.api.share.ShareVos.PublicAlbumVo;
import com.hanamizuki.backend.service.ShareService;

/**
 * The public face of an album: no token in a header, no login, no SPA.
 *
 * <p>{@code /s/{token}} is rendered on the server because the LINE crawler does
 * not execute JavaScript. A React page would give it a card with no title and
 * no image, which is precisely the moment the demo is supposed to land.
 */
@Controller
public class PublicShareController {

    private final ShareService shareService;

    public PublicShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /** The page a crawler reads and a friend opens. */
    @GetMapping("/s/{token}")
    public String page(@PathVariable String token, Model model) {
        PublicAlbumVo album = shareService.view(token);
        model.addAttribute("album", album);
        // Absolute, because og:image is fetched by a crawler that has no idea
        // what page it came from.
        model.addAttribute("ogImage", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/og/" + token + ".jpg").build().toUriString());
        model.addAttribute("shareUrl", ServletUriComponentsBuilder.fromCurrentRequest()
                .build().toUriString());
        return "share";
    }

    /** Same data as the page, for anything that would rather have JSON. */
    @GetMapping("/api/share/{token}")
    @ResponseBody
    public PublicAlbumVo json(@PathVariable String token) {
        return shareService.view(token);
    }

    /** Decorated photo bytes, gated by the same token as the page. */
    @GetMapping("/s/{token}/photos/{albumPhotoId}")
    @ResponseBody
    public ResponseEntity<byte[]> photo(@PathVariable String token,
                                        @PathVariable Long albumPhotoId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("Cache-Control", "public, max-age=300")
                .body(shareService.sharedPhoto(token, albumPhotoId));
    }

    @GetMapping("/og/{token}.jpg")
    @ResponseBody
    public ResponseEntity<byte[]> ogImage(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                // Short, not immutable: decorating a photo changes the card,
                // and a crawler that cached it for a year would never notice.
                .header("Cache-Control", "public, max-age=300")
                .body(shareService.ogImage(token));
    }
}
