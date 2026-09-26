package com.hanamizuki.backend.api.album;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.album.AlbumRequests.AddMemberRequest;
import com.hanamizuki.backend.api.album.AlbumRequests.AlbumPatchRequest;
import com.hanamizuki.backend.api.album.AlbumRequests.AlbumPhotoPatchRequest;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumPhotoVo;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumSummaryVo;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;
import com.hanamizuki.backend.api.job.JobVos.GenerateJobVo;
import com.hanamizuki.backend.api.job.JobVos.GenerateRequest;
import com.hanamizuki.backend.api.job.JobVos.JobAcceptedVo;
import com.hanamizuki.backend.common.ETags;
import com.hanamizuki.backend.common.PageVo;
import com.hanamizuki.backend.domain.AlbumJob;
import com.hanamizuki.backend.security.AuthUser;
import com.hanamizuki.backend.service.AlbumGenerationRunner;
import com.hanamizuki.backend.service.AlbumGenerationService;
import com.hanamizuki.backend.service.AlbumService;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumService albumService;
    private final AlbumGenerationService generationService;
    private final AlbumGenerationRunner runner;

    public AlbumController(AlbumService albumService,
                           AlbumGenerationService generationService,
                           AlbumGenerationRunner runner) {
        this.albumService = albumService;
        this.generationService = generationService;
        this.runner = runner;
    }

    /**
     * Starts a generation and answers 202 straight away. One request can
     * produce several albums, so what comes back is a job id, not an album id.
     */
    @PostMapping("/generate")
    public ResponseEntity<JobAcceptedVo> generate(
            @RequestBody GenerateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthUser principal) {
        AlbumJob job = generationService.accept(principal.userId(), request.photoIds(), idempotencyKey);
        // Only kick off work for a job that has not run before; a replayed
        // key returns the original rather than starting a second run.
        if (job.getStartTime() == null) {
            runner.run(job.getId());
        }
        return ResponseEntity.accepted().body(new JobAcceptedVo(job.getId()));
    }

    @GetMapping("/jobs/{jobId}")
    public GenerateJobVo job(@PathVariable Long jobId,
                             @AuthenticationPrincipal AuthUser principal) {
        return generationService.status(jobId, principal.userId());
    }

    /**
     * ホーム画面。/ 首页。
     *
     * <p>Declared before {@code /{albumId}} is irrelevant to Spring, which
     * matches the literal path first — but the two are easy to confuse when
     * reading, so the list stays next to its own summary type.
     */
    @GetMapping
    public PageVo<AlbumSummaryVo> list(@RequestParam(defaultValue = "50") int limit,
                                       @RequestParam(required = false) Long memberId,
                                       @AuthenticationPrincipal AuthUser principal) {
        return albumService.list(principal.userId(), Math.min(Math.max(limit, 1), 100), memberId);
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
                .eTag(ETags.of(album.version()))
                .body(album);
    }

    /** Title, cover and summary. The new version comes back as a fresh ETag. */
    @PatchMapping("/{albumId}")
    public ResponseEntity<AlbumVo> patch(
            @PathVariable Long albumId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody AlbumPatchRequest request,
            @AuthenticationPrincipal AuthUser principal) {
        AlbumVo album = albumService.patch(albumId, principal.userId(),
                ETags.parse(ifMatch), request);
        return ResponseEntity.ok().eTag(ETags.of(album.version())).body(album);
    }

    /**
     * The caption card on screen 05. Versioned per {@code album_photo} row, not
     * per album, so two people captioning different photos never collide.
     */
    @PatchMapping("/{albumId}/photos/{albumPhotoId}")
    public ResponseEntity<AlbumPhotoVo> patchPhoto(
            @PathVariable Long albumId,
            @PathVariable Long albumPhotoId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody AlbumPhotoPatchRequest request,
            @AuthenticationPrincipal AuthUser principal) {
        AlbumPhotoVo photo = albumService.patchPhoto(albumId, albumPhotoId,
                principal.userId(), ETags.parse(ifMatch), request);
        return ResponseEntity.ok().eTag(ETags.of(photo.version())).body(photo);
    }

    /** 204 either way: inviting someone already in the album is not an error. */
    @PostMapping("/{albumId}/members")
    public ResponseEntity<Void> addMember(@PathVariable Long albumId,
                                          @RequestBody AddMemberRequest request,
                                          @AuthenticationPrincipal AuthUser principal) {
        albumService.addMember(albumId, principal.userId(), request.userId());
        return ResponseEntity.noContent().build();
    }
}
