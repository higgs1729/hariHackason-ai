package com.hanamizuki.backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.job.JobVos.GenerateJobVo;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumJob;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.enums.JobStatus;
import com.hanamizuki.backend.domain.enums.MemberRole;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.integration.ai.AlbumDraft;
import com.hanamizuki.backend.integration.ai.AlbumEnricher;
import com.hanamizuki.backend.integration.ai.PhotoInsight;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.AlbumJobRepository;
import com.hanamizuki.backend.repository.AlbumMemberRepository;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.AlbumRepository;
import com.hanamizuki.backend.repository.PhotoRepository;
import com.hanamizuki.backend.repository.UserRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Turning a pile of photos into albums.
 *
 * <p>One request can produce several: thirty photos spanning two afternoons are
 * two albums, which is why the client polls a job rather than an album id.
 *
 * <p>The AI is an enhancement layered on top, not a dependency. Clustering runs
 * first, is deterministic and offline, and already produces usable albums; a
 * failed Claude call leaves the job READY with {@code aiGenerated = false}
 * rather than failing it. FAILED is reserved for bad input.
 */
@Service
public class AlbumGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AlbumGenerationService.class);
    private static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final AlbumJobRepository jobs;
    private final PhotoRepository photos;
    private final AlbumRepository albums;
    private final AlbumMemberRepository albumMembers;
    private final AlbumPhotoRepository albumPhotos;
    private final UserRepository users;
    private final PhotoClusterer clusterer;
    private final AlbumEnricher enricher;
    private final ObjectMapper objectMapper;

    public AlbumGenerationService(AlbumJobRepository jobs, PhotoRepository photos,
                                  AlbumRepository albums, AlbumMemberRepository albumMembers,
                                  AlbumPhotoRepository albumPhotos, UserRepository users,
                                  PhotoClusterer clusterer, AlbumEnricher enricher,
                                  ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.photos = photos;
        this.albums = albums;
        this.albumMembers = albumMembers;
        this.albumPhotos = albumPhotos;
        this.users = users;
        this.clusterer = clusterer;
        this.enricher = enricher;
        this.objectMapper = objectMapper;
    }

    /**
     * Accepts the request and records the job. Returns immediately; the work
     * happens on the generation executor.
     */
    @Transactional
    public AlbumJob accept(Long userId, List<Long> photoIds, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key is required on this route");
        }
        // A request that timed out at the network layer may never have arrived,
        // so the concurrency check below would not catch the retry. The key is
        // what stops a flaky connection from paying for two Claude runs.
        AlbumJob replay = jobs.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (replay != null) {
            return replay;
        }

        boolean running = !jobs.findByUserIdAndStatusIn(userId,
                List.of(JobStatus.PENDING, JobStatus.CLUSTERING, JobStatus.ENRICHING)).isEmpty();
        if (running) {
            throw new ApiException(ErrorCode.JOB_ALREADY_RUNNING,
                    "An album is already being generated");
        }

        List<Photo> selected = photos.findAllById(photoIds).stream()
                .filter(photo -> photo.getUserId().equals(userId))
                .toList();
        if (selected.isEmpty()) {
            throw new ApiException(ErrorCode.NO_VALID_PHOTOS, "None of those photos are yours");
        }

        AlbumJob job = new AlbumJob();
        job.setUserId(userId);
        job.setPhotoIds(objectMapper.writeValueAsString(selected.stream().map(Photo::getId).toList()));
        job.setPhotoNum(selected.size());
        job.setStatus(JobStatus.PENDING);
        job.setIdempotencyKey(idempotencyKey);
        return jobs.save(job);
    }

    /**
     * The job itself. Called on the generation executor, never from a request
     * thread.
     */
    @Transactional
    public void execute(Long jobId) {
        AlbumJob job = jobs.findById(jobId).orElseThrow();
        job.setStartTime(LocalDateTime.now());

        try {
            List<Long> photoIds = objectMapper.readValue(job.getPhotoIds(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            User owner = users.findById(job.getUserId()).orElseThrow();
            List<Photo> selected = photos.findAllById(photoIds);

            job.setStatus(JobStatus.CLUSTERING);
            job.setProgress(10);
            List<List<Photo>> clusters = clusterer.cluster(selected);
            if (clusters.isEmpty()) {
                fail(job, ErrorCode.NO_VALID_PHOTOS, "Nothing to cluster");
                return;
            }

            job.setStatus(JobStatus.ENRICHING);
            List<Long> albumIds = new java.util.ArrayList<>();
            boolean anyAi = false;
            boolean aiGaveUp = false;
            for (int i = 0; i < clusters.size(); i++) {
                List<Photo> cluster = clusters.get(i);
                // Once a call has failed, stop trying. The failure mode that
                // matters at a venue is a dead network, and that fails by
                // timing out: retrying per cluster would make the user wait
                // the full timeout again for each one before anything appears.
                AlbumDraft draft = aiGaveUp ? null : describe(cluster);
                aiGaveUp |= draft == null;
                anyAi |= draft != null;
                albumIds.add(assemble(cluster, owner, draft).getId());
                // 10 at the start, 90 by the last cluster: the client shows
                // movement instead of a spinner that never changes.
                job.setProgress(10 + (80 * (i + 1) / clusters.size()));
            }

            job.setAiGenerated(anyAi);
            job.setAlbumIds(objectMapper.writeValueAsString(albumIds));
            job.setAlbumNum(albumIds.size());
            job.setStatus(JobStatus.READY);
            job.setProgress(100);
        } catch (Exception e) {
            log.error("Generation job {} failed", jobId, e);
            fail(job, ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            job.setFinishTime(LocalDateTime.now());
            if (job.getStartTime() != null) {
                job.setCostMs(java.time.Duration.between(job.getStartTime(), job.getFinishTime())
                        .toMillis());
            }
        }
    }

    /**
     * Asks Claude to name this cluster, or returns null.
     *
     * <p>Null is an ordinary outcome, not an error: no API key, a timeout, a
     * refusal, a venue with no working wifi. The album still gets made with a
     * date-based title. The one thing that must never happen is the user
     * seeing that the AI failed, so nothing here escapes.
     */
    private AlbumDraft describe(List<Photo> cluster) {
        if (!enricher.isAvailable()) {
            return null;
        }
        try {
            return enricher.enrich(cluster);
        } catch (Exception e) {
            log.warn("Enrichment failed for a cluster of {}; falling back to a rule-based title",
                    cluster.size(), e);
            return null;
        }
    }

    /**
     * Builds one album out of one cluster.
     *
     * @param draft Claude's copy, or null to use rule-based titles
     */
    private Album assemble(List<Photo> cluster, User owner, AlbumDraft draft) {
        Photo cover = pickCover(cluster, draft);

        Album album = new Album();
        album.setTitle(draft != null && draft.title() != null
                ? draft.title()
                : cluster.get(0).getTakenTime().format(TITLE_DATE) + " のアルバム");
        album.setSummary(draft == null ? null : draft.summary());
        album.setAlbumDate(cluster.get(0).getTakenTime().toLocalDate());
        album.setAiGenerated(draft != null);
        album.setAiModel(draft == null ? null : enricher.model());
        album.setUserId(owner.getId());
        album.setUserName(owner.getUserName());
        album.setPhotoNum(cluster.size());
        album.setMemberNum(1);
        album.setCoverPhotoId(cover.getId());
        album.setCoverPhotoUrl(cover.getFilePath());
        album.setCoverThumbUrl(cover.getThumbPath());
        albums.save(album);

        AlbumMember member = new AlbumMember();
        member.setAlbumId(album.getId());
        member.setUserId(owner.getId());
        member.setMemberRole(MemberRole.OWNER);
        member.setUserName(owner.getUserName());
        member.setUserAvatar(owner.getUserAvatar());
        member.setAlbumTitle(album.getTitle());
        albumMembers.save(member);

        for (int i = 0; i < cluster.size(); i++) {
            Photo photo = cluster.get(i);
            AlbumPhoto ap = new AlbumPhoto();
            ap.setAlbumId(album.getId());
            ap.setPhotoId(photo.getId());
            ap.setPosition(i);
            // Copied in, not joined later: this is what lets the album screen
            // read two tables and no more.
            ap.setPhotoUrl(photo.getFilePath());
            ap.setThumbUrl(photo.getThumbPath());
            ap.setPicWidth(photo.getPicWidth());
            ap.setPicHeight(photo.getPicHeight());
            ap.setPicScale(photo.getPicScale());
            ap.setTakenTime(photo.getTakenTime());

            PhotoInsight insight = insightFor(draft, photo.getId());
            if (insight != null) {
                ap.setCaption(insight.caption());
                ap.setPlace(insight.place());
                ap.setWeather(insight.weather());
                ap.setPhotoComment(insight.comment());
            }
            albumPhotos.save(ap);

            photo.setAlbumNum(photo.getAlbumNum() + 1);
        }

        owner.setAlbumNum(owner.getAlbumNum() + 1);
        return album;
    }

    /** The model's pick when it named one of ours, otherwise the earliest shot. */
    private Photo pickCover(List<Photo> cluster, AlbumDraft draft) {
        if (draft == null || draft.coverPhotoId() == null) {
            return cluster.get(0);
        }
        return cluster.stream()
                .filter(photo -> photo.getId().equals(draft.coverPhotoId()))
                .findFirst()
                .orElse(cluster.get(0));
    }

    private PhotoInsight insightFor(AlbumDraft draft, Long photoId) {
        if (draft == null || draft.photos() == null) {
            return null;
        }
        return draft.photos().stream()
                .filter(insight -> photoId.equals(insight.photoId()))
                .findFirst()
                .orElse(null);
    }

    private void fail(AlbumJob job, ErrorCode code, String message) {
        job.setStatus(JobStatus.FAILED);
        job.setErrorCode(code.name());
        job.setErrorMsg(message);
    }

    @Transactional(readOnly = true)
    public GenerateJobVo status(Long jobId, Long userId) {
        AlbumJob job = jobs.findById(jobId)
                .orElseThrow(() -> new ApiException(ErrorCode.JOB_NOT_FOUND));
        if (!job.getUserId().equals(userId)) {
            // Not 403: knowing the job exists is already more than a stranger
            // should learn from a guessable id.
            throw new ApiException(ErrorCode.JOB_NOT_FOUND);
        }
        List<Long> albumIds = job.getAlbumIds() == null
                ? List.of()
                : objectMapper.readValue(job.getAlbumIds(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        return GenerateJobVo.of(job, albumIds);
    }
}
