package com.hanamizuki.backend.api.album;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;
import com.hanamizuki.backend.api.job.JobVos.GenerateJobVo;
import com.hanamizuki.backend.api.job.JobVos.GenerateRequest;
import com.hanamizuki.backend.api.job.JobVos.JobAcceptedVo;
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
