package com.hanamizuki.backend.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Exists only so the {@code @Async} boundary is a real one.
 *
 * <p>Calling an {@code @Async} method from inside the same bean bypasses the
 * proxy and runs it on the caller's thread — the request would then block for
 * the whole generation. A separate bean makes the hop explicit.
 */
@Component
public class AlbumGenerationRunner {

    private final AlbumGenerationService service;

    public AlbumGenerationRunner(AlbumGenerationService service) {
        this.service = service;
    }

    @Async("generationExecutor")
    public void run(Long jobId) {
        service.execute(jobId);
    }
}
