package com.hanamizuki.backend.api.job;

import java.time.OffsetDateTime;
import java.util.List;

import com.hanamizuki.backend.common.Times;
import com.hanamizuki.backend.domain.AlbumJob;

public final class JobVos {

    private JobVos() {
    }

    /** What the client polls while the spinner is on screen. */
    public record GenerateJobVo(Long id, String status, int progress,
                                List<Long> albumIds, String errorMsg,
                                OffsetDateTime finishTime) {

        public static GenerateJobVo of(AlbumJob job, List<Long> albumIds) {
            return new GenerateJobVo(job.getId(), job.getStatus().name(), job.getProgress(),
                    albumIds, job.getErrorMsg(), Times.toOffset(job.getFinishTime()));
        }
    }

    public record GenerateRequest(List<Long> photoIds) {
    }

    public record JobAcceptedVo(Long jobId) {
    }
}
