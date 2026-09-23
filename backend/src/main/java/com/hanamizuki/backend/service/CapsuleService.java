package com.hanamizuki.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleOpenedVo;
import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleSealedVo;
import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleVo;
import com.hanamizuki.backend.api.capsule.CapsuleVos.CreateCapsuleRequest;
import com.hanamizuki.backend.common.Times;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.Capsule;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.CapsuleRepository;
import com.hanamizuki.backend.repository.UserRepository;

/**
 * An album sealed until a date.
 *
 * <p>The whole feature is one rule: before {@code openTime}, the server does
 * not hand over the message or the album. Hiding them in the frontend is not
 * the feature — anyone with dev tools would see straight through it — so the
 * decision is made here, before a response object exists to put them in.
 */
@Service
public class CapsuleService {

    private final CapsuleRepository capsules;
    private final UserRepository users;
    private final AlbumService albumService;

    public CapsuleService(CapsuleRepository capsules, UserRepository users,
                          AlbumService albumService) {
        this.capsules = capsules;
        this.users = users;
        this.albumService = albumService;
    }

    @Transactional
    public CapsuleVo create(CreateCapsuleRequest request, Long userId) {
        albumService.requireMember(request.albumId(), userId);
        Album album = albumService.require(request.albumId());

        capsules.findByAlbumId(request.albumId()).ifPresent(existing -> {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "This album is already in a capsule");
        });

        LocalDateTime openAt = toLocal(request.openTime());
        if (openAt == null || !openAt.isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "openTime has to be in the future");
        }

        Capsule capsule = new Capsule();
        capsule.setAlbumId(album.getId());
        capsule.setUserId(userId);
        // Snapshot: what gets sealed is the album as it is today, not whatever
        // it has been renamed to by the time it opens.
        capsule.setAlbumTitle(album.getTitle());
        capsule.setCoverPhotoUrl(album.getCoverPhotoUrl());
        capsule.setPhotoNum(album.getPhotoNum());
        capsule.setCapsuleMsg(request.capsuleMsg());
        capsule.setOpenTime(openAt);
        capsules.save(capsule);

        users.findById(userId).ifPresent(user -> user.setCapsuleNum(user.getCapsuleNum() + 1));
        return sealed(capsule);
    }

    @Transactional(readOnly = true)
    public List<CapsuleVo> list(Long userId) {
        return capsules.findByUserIdOrderByOpenTimeAsc(userId).stream()
                .map(capsule -> capsule.getOpenedTime() == null
                        ? (CapsuleVo) sealed(capsule)
                        : opened(capsule, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public CapsuleVo get(Long capsuleId, Long userId) {
        Capsule capsule = require(capsuleId, userId);
        return capsule.getOpenedTime() == null ? sealed(capsule) : opened(capsule, userId);
    }

    /**
     * @throws ApiException 409 while the date is still in the future — the
     *         check is here rather than on the client, which is the point
     */
    @Transactional
    public CapsuleVo open(Long capsuleId, Long userId) {
        Capsule capsule = require(capsuleId, userId);
        if (capsule.getOpenedTime() != null) {
            return opened(capsule, userId);
        }
        if (capsule.getOpenTime().isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.CAPSULE_NOT_YET_OPEN,
                    "This capsule opens on " + capsule.getOpenTime().toLocalDate());
        }
        capsule.setOpenedTime(LocalDateTime.now());
        return opened(capsule, userId);
    }

    /**
     * Brings the open date forward to now. Dev only.
     *
     * <p>The poster's capsule opens a year later and the demo is five minutes
     * long, so there has to be a way to show the payoff. It is a separate
     * method rather than a flag on {@link #open} so that the real path keeps
     * exactly one behaviour.
     */
    @Transactional
    public CapsuleVo unsealNow(Long capsuleId, Long userId) {
        Capsule capsule = require(capsuleId, userId);
        capsule.setOpenTime(LocalDateTime.now().minusSeconds(1));
        return open(capsuleId, userId);
    }

    @Transactional
    public void delete(Long capsuleId, Long userId) {
        Capsule capsule = require(capsuleId, userId);
        capsule.setDelete(true);
        users.findById(userId).ifPresent(user ->
                user.setCapsuleNum(Math.max(0, user.getCapsuleNum() - 1)));
    }

    /**
     * Owner only, and a stranger gets 404 rather than 403: that a capsule
     * exists at all is more than someone guessing ids should learn.
     */
    private Capsule require(Long capsuleId, Long userId) {
        Capsule capsule = capsules.findById(capsuleId)
                .orElseThrow(() -> new ApiException(ErrorCode.CAPSULE_NOT_FOUND));
        if (!capsule.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.CAPSULE_NOT_FOUND);
        }
        return capsule;
    }

    private CapsuleSealedVo sealed(Capsule capsule) {
        long days = Math.max(0,
                Duration.between(LocalDateTime.now(), capsule.getOpenTime()).toDays());
        return new CapsuleSealedVo(capsule.getId(), "SEALED",
                Times.toOffset(capsule.getOpenTime()), days);
    }

    private CapsuleOpenedVo opened(Capsule capsule, Long userId) {
        return new CapsuleOpenedVo(capsule.getId(), "OPENED",
                Times.toOffset(capsule.getOpenTime()),
                Times.toOffset(capsule.getOpenedTime()),
                capsule.getCapsuleMsg(),
                albumService.get(capsule.getAlbumId(), userId));
    }

    /**
     * The column is a naive datetime in the server's zone, so an incoming
     * instant has to be expressed in that zone before it is stored.
     *
     * <p>{@code toLocalDateTime()} alone would drop whatever offset Jackson
     * had already normalised the value to — midnight in Tokyo arrives as
     * 15:00 the previous day in UTC, and the capsule would open nine hours
     * early.
     */
    private static LocalDateTime toLocal(OffsetDateTime value) {
        return value == null
                ? null
                : value.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
