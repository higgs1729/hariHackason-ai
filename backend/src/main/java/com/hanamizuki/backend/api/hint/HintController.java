package com.hanamizuki.backend.api.hint;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.hint.HintVos.ShootHintRequest;
import com.hanamizuki.backend.api.hint.HintVos.ShootHintVo;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.ShootHintService;

/**
 * カメラ画面の「こんな写真を撮ろう」。/ 相机页的拍照建议。
 *
 * <p>POST rather than GET even though nothing is stored: the body carries names
 * the user typed, and those have no business sitting in a URL, a proxy log or
 * a browser history entry.
 */
@RestController
@RequestMapping("/api/hints")
public class HintController {

    private final ShootHintService hintService;

    public HintController(ShootHintService hintService) {
        this.hintService = hintService;
    }

    /**
     * Always 200 with usable text, except when the caller is over the rate
     * limit. Everything else — no API key, a timeout, a malformed answer —
     * comes back as the canned hint.
     */
    @PostMapping("/shoot")
    public ShootHintVo shoot(@RequestBody ShootHintRequest request,
                             @AuthenticationPrincipal AuthUser principal) {
        return hintService.suggest(request, principal.userId());
    }
}
