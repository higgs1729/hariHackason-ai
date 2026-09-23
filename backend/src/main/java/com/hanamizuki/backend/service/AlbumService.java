package com.hanamizuki.backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.album.AlbumRequests.AlbumPatchRequest;
import com.hanamizuki.backend.api.album.AlbumRequests.AlbumPhotoPatchRequest;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumPhotoVo;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumSummaryVo;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;
import com.hanamizuki.backend.common.PageVo;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.enums.MemberRole;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.AlbumMemberRepository;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.AlbumRepository;
import com.hanamizuki.backend.repository.UserRepository;

/** Reading and editing an album, and the membership check every album endpoint shares. */
@Service
public class AlbumService {

    /** types.ts types weather as a closed union, so the server keeps the same four. */
    private static final Set<String> WEATHER = Set.of("晴れ", "曇り", "雨", "雪");

    private final AlbumRepository albums;
    private final AlbumMemberRepository members;
    private final AlbumPhotoRepository albumPhotos;
    private final UserRepository users;

    public AlbumService(AlbumRepository albums, AlbumMemberRepository members,
                        AlbumPhotoRepository albumPhotos, UserRepository users) {
        this.albums = albums;
        this.members = members;
        this.albumPhotos = albumPhotos;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public AlbumVo get(Long albumId, Long userId) {
        Album album = albums.findById(albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        AlbumMember me = requireMember(albumId, userId);

        List<AlbumMember> allMembers = members.findByAlbumIdOrderByIdAsc(albumId);
        List<AlbumPhoto> rows = albumPhotos.findByAlbumIdOrderByPositionAsc(albumId);

        return AlbumVo.of(album, me.getMemberRole().name().toLowerCase(), allMembers, rows);
    }

    /**
     * ホーム画面のアルバム一覧。/ 首页相册列表。
     *
     * <p>Two queries regardless of how many albums come back: the membership
     * rows, then the albums by id. Reading each album to find its cover photo
     * would be the N+1 that the denormalised cover columns exist to avoid.
     *
     * <p>Driven off {@code album_member} rather than {@code album.userId}, so an
     * album someone added me to appears on my home screen too.
     *
     * <p>{@code nextCursor} stays null: the list is capped at {@code limit} and
     * an account here has tens of albums, not thousands. The envelope keeps the
     * field so paging can arrive later without the response shape changing.
     */
    @Transactional(readOnly = true)
    public PageVo<AlbumSummaryVo> list(Long userId, int limit) {
        List<AlbumMember> mine = members.findByUserIdOrderByIdDesc(userId);
        if (mine.isEmpty()) {
            return PageVo.of(List.of(), 0);
        }
        Map<Long, String> roleByAlbum = mine.stream().collect(Collectors.toMap(
                AlbumMember::getAlbumId,
                member -> member.getMemberRole().name().toLowerCase(),
                (first, second) -> first));

        List<AlbumSummaryVo> items = albums
                .findByIdInOrderByAlbumDateDescIdDesc(roleByAlbum.keySet())
                .stream()
                .limit(limit)
                .map(album -> AlbumSummaryVo.of(album, roleByAlbum.get(album.getId())))
                .toList();

        // total is how many the user has, not how many this page returned.
        return PageVo.of(items, roleByAlbum.size());
    }

    /**
     * タイトル・表紙・要約の編集。/ 编辑标题、封面、摘要。
     *
     * @param ifMatch the version from the ETag; absent is 428, stale is 409
     */
    @Transactional
    public AlbumVo patch(Long albumId, Long userId, Integer ifMatch, AlbumPatchRequest request) {
        requireMember(albumId, userId);
        Album album = require(albumId);
        checkVersion(album, ifMatch);

        String newTitle = null;
        if (request.title() != null) {
            newTitle = request.title().strip();
            if (newTitle.isEmpty() || newTitle.length() > 512) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "title has to be 1-512 characters");
            }
            album.setTitle(newTitle);
        }
        if (request.summary() != null) {
            album.setSummary(limit(request.summary(), 1024, "summary"));
        }
        if (request.coverPhotoId() != null) {
            setCover(album, request.coverPhotoId());
        }
        // Flushed here so @Version is bumped before the response is built.
        albums.saveAndFlush(album);

        // album_member.albumTitle is a follow copy, so the rename has to reach
        // it — otherwise every member's list keeps showing the old title until
        // those rows are next rewritten.
        //
        // Runs after the flush, not before: the bulk update clears the
        // persistence context, which would detach `album` and turn the save
        // into a merge — a second flush, and a version that jumps by two on a
        // title change but by one on anything else.
        if (newTitle != null) {
            members.retitle(albumId, newTitle);
        }
        return get(albumId, userId);
    }

    /**
     * The cover paths are copied onto {@code album} so the list never joins
     * {@code photo}. Moving the id without them would leave every list showing
     * the previous cover — exactly the drift the column comments warn about.
     */
    private void setCover(Album album, Long coverPhotoId) {
        AlbumPhoto row = albumPhotos.findByAlbumIdOrderByPositionAsc(album.getId()).stream()
                .filter(candidate -> coverPhotoId.equals(candidate.getPhotoId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                        "That photo is not in this album"));
        album.setCoverPhotoId(row.getPhotoId());
        album.setCoverPhotoUrl(row.getPhotoUrl());
        album.setCoverThumbUrl(row.getThumbUrl());
    }

    /**
     * 写真ごとのキャプション編集。/ 编辑单张照片的文案。
     *
     * <p>Shares {@code album_photo.version} with the decoration route on
     * purpose: a caption and a drawing on the same photo are the same row, and
     * separate version counters would let one overwrite the other silently.
     */
    @Transactional
    public AlbumPhotoVo patchPhoto(Long albumId, Long albumPhotoId, Long userId,
                                   Integer ifMatch, AlbumPhotoPatchRequest request) {
        requireMember(albumId, userId);
        AlbumPhoto row = albumPhotos.findByIdAndAlbumId(albumPhotoId, albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND));
        checkVersion(row, ifMatch);

        if (request.caption() != null) {
            row.setCaption(limit(request.caption(), 512, "caption"));
        }
        if (request.place() != null) {
            row.setPlace(limit(request.place(), 256, "place"));
        }
        if (request.photoComment() != null) {
            row.setPhotoComment(limit(request.photoComment(), 512, "photoComment"));
        }
        if (request.music() != null) {
            row.setMusic(limit(request.music(), 256, "music"));
        }
        if (request.weather() != null) {
            String weather = request.weather().strip();
            if (!WEATHER.contains(weather)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "weather has to be one of " + WEATHER);
            }
            row.setWeather(weather);
        }
        albumPhotos.saveAndFlush(row);
        return AlbumPhotoVo.of(row);
    }

    /**
     * メンバー追加。/ 添加相册成员。
     *
     * <p>Any member can invite, and there is no approval step: the album belongs
     * to a group who were together when the photos were taken, and a request
     * queue for that is ceremony nobody would use.
     */
    @Transactional
    public void addMember(Long albumId, Long userId, Long inviteeId) {
        requireMember(albumId, userId);
        Album album = require(albumId);

        User invitee = users.findById(inviteeId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        // Checked rather than left to uk_albumId_userId, which would surface as
        // a 500. Inviting someone twice is not a server error, so it is a no-op.
        if (members.existsByAlbumIdAndUserId(albumId, inviteeId)) {
            return;
        }

        AlbumMember member = new AlbumMember();
        member.setAlbumId(albumId);
        member.setUserId(inviteeId);
        member.setMemberRole(MemberRole.EDITOR);
        // Follow copies, written now and refreshed by UserService on rename.
        member.setUserName(invitee.getUserName());
        member.setUserAvatar(invitee.getUserAvatar());
        member.setAlbumTitle(album.getTitle());
        members.save(member);

        album.setMemberNum(album.getMemberNum() + 1);
        invitee.setAlbumNum(invitee.getAlbumNum() + 1);
    }

    /**
     * Ids are sequential and therefore guessable, so this runs on every album
     * route rather than being assumed from the fact that the caller is logged
     * in. Missing it once is an IDOR.
     */
    @Transactional(readOnly = true)
    public AlbumMember requireMember(Long albumId, Long userId) {
        return members.findByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_FORBIDDEN,
                        "You are not a member of this album"));
    }

    @Transactional(readOnly = true)
    public Album require(Long albumId) {
        return albums.findById(albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
    }

    private void checkVersion(Album album, Integer ifMatch) {
        if (ifMatch == null) {
            throw new ApiException(ErrorCode.IF_MATCH_REQUIRED,
                    "If-Match is required on this route");
        }
        if (ifMatch != album.getVersion()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT,
                    "Someone else changed this album first",
                    Map.of("current", album.getVersion()));
        }
    }

    private void checkVersion(AlbumPhoto row, Integer ifMatch) {
        if (ifMatch == null) {
            throw new ApiException(ErrorCode.IF_MATCH_REQUIRED,
                    "If-Match is required on this route");
        }
        if (ifMatch != row.getVersion()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT,
                    "Someone else changed this photo first",
                    Map.of("current", row.getVersion()));
        }
    }

    /** Empty clears the field; over-length is a 400 rather than a silent truncation. */
    private static String limit(String value, int max, String field) {
        String text = value.strip();
        if (text.length() > max) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    field + " has to be at most " + max + " characters");
        }
        return text.isEmpty() ? null : text;
    }
}
