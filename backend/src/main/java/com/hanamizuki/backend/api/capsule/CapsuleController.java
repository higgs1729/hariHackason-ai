package com.hanamizuki.backend.api.capsule;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleVo;
import com.hanamizuki.backend.api.capsule.CapsuleVos.CreateCapsuleRequest;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.CapsuleService;

/** Screens 06 and 07. */
@RestController
@RequestMapping("/api/capsules")
public class CapsuleController {

    private final CapsuleService capsuleService;

    public CapsuleController(CapsuleService capsuleService) {
        this.capsuleService = capsuleService;
    }

    @PostMapping
    public CapsuleVo create(@RequestBody CreateCapsuleRequest request,
                            @AuthenticationPrincipal AuthUser principal) {
        return capsuleService.create(request, principal.userId());
    }

    @GetMapping
    public List<CapsuleVo> list(@AuthenticationPrincipal AuthUser principal) {
        return capsuleService.list(principal.userId());
    }

    /** Returns the sealed shape until it is opened; there is no way to peek. */
    @GetMapping("/{capsuleId}")
    public CapsuleVo get(@PathVariable Long capsuleId,
                         @AuthenticationPrincipal AuthUser principal) {
        return capsuleService.get(capsuleId, principal.userId());
    }

    @PostMapping("/{capsuleId}/open")
    public CapsuleVo open(@PathVariable Long capsuleId,
                          @AuthenticationPrincipal AuthUser principal) {
        return capsuleService.open(capsuleId, principal.userId());
    }

    @DeleteMapping("/{capsuleId}")
    public ResponseEntity<Void> delete(@PathVariable Long capsuleId,
                                       @AuthenticationPrincipal AuthUser principal) {
        capsuleService.delete(capsuleId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
