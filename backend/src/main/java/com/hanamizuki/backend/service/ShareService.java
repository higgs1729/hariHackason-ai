package com.hanamizuki.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.share.ShareVos.PublicAlbumVo;
import com.hanamizuki.backend.api.share.ShareVos.PublicPhotoVo;
import com.hanamizuki.backend.api.share.ShareVos.ShareLinkVo;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.domain.AlbumShare;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.integration.image.CollageRenderer;
import com.hanamizuki.backend.integration.storage.FileStorage;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.AlbumRepository;
import com.hanamizuki.backend.repository.AlbumShareRepository;

/**
 * Public links to an album.
 *
 * <p>The token is random rather than the album id because this page needs no
 * login: a sequential identifier would let anyone read every album by counting
 * upwards.
 *
 * <p>The album fields on the share row are a snapshot taken when the link was
 * created and deliberately do not follow the album afterwards. A link already
 * sitting in a LINE conversation should not change what it claims to be.
 */
@Service
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AlbumShareRepository shares;
    private final AlbumRepository albums;
    private final AlbumPhotoRepository albumPhotos;
    private final AlbumService albumService;
    private final FileStorage storage;
    private final CollageRenderer collage;

    public ShareService(AlbumShareRepository shares, AlbumRepository albums,
                        AlbumPhotoRepository albumPhotos, AlbumService albumService,
                        FileStorage storage, CollageRenderer collage) {
        this.shares = shares;
        this.albums = albums;
        this.albumPhotos = albumPhotos;
        this.albumService = albumService;
        this.storage = storage;
        this.collage = collage;
    }

    /** Sharing twice returns the same link, so a URL a friend already has keeps working. */
    @Transactional
    public ShareLinkVo share(Long albumId, Long userId, String baseUrl) {
        albumService.requireMember(albumId, userId);
        Album album = albumService.require(albumId);

        AlbumShare share = shares.findByAlbumIdAndRevokeTimeIsNull(albumId).orElseGet(() -> {
            AlbumShare created = new AlbumShare();
            created.setShareToken(newToken());
            created.setAlbumId(albumId);
            created.setUserId(userId);
            created.setAlbumTitle(album.getTitle());
            created.setAlbumSummary(album.getSummary());
            created.setAlbumDate(album.getAlbumDate());
            created.setCoverPhotoUrl(album.getCoverPhotoUrl());
            created.setPhotoNum(album.getPhotoNum());
            shares.save(created);

            album.setShareToken(created.getShareToken());
            return created;
        });

        return toLink(share, baseUrl);
    }

    @Transactional(readOnly = true)
    public ShareLinkVo current(Long albumId, Long userId, String baseUrl) {
        albumService.requireMember(albumId, userId);
        AlbumShare share = shares.findByAlbumIdAndRevokeTimeIsNull(albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_REVOKED,
                        "This album is not shared"));
        return toLink(share, baseUrl);
    }

    @Transactional
    public void revoke(Long albumId, Long userId) {
        albumService.requireMember(albumId, userId);
        shares.findByAlbumIdAndRevokeTimeIsNull(albumId).ifPresent(share -> {
            share.setRevokeTime(LocalDateTime.now());
            albums.findById(albumId).ifPresent(album -> album.setShareToken(null));
        });
    }

    /**
     * Resolves a token for public consumption and counts the view.
     *
     * <p>A dead link answers 410 rather than 404, so the page can say the album
     * is no longer shared instead of implying it never existed.
     */
    @Transactional
    public PublicAlbumVo view(String token) {
        AlbumShare share = require(token);
        share.setViewNum(share.getViewNum() + 1);

        Album album = albums.findById(share.getAlbumId()).orElse(null);
        if (album != null) {
            album.setViewNum(album.getViewNum() + 1);
        }

        List<PublicPhotoVo> photos = albumPhotos
                .findByAlbumIdOrderByPositionAsc(share.getAlbumId()).stream()
                // Scoped by the share token rather than the album id. The
                // /api/albums/... composite route needs a logged-in member, and
                // opening that route to everyone would let anyone walk the
                // decorated photos of every album by counting ids.
                .map(ap -> new PublicPhotoVo(
                        "/s/%s/photos/%d".formatted(token, ap.getId()),
                        ap.getCaption()))
                .toList();

        return new PublicAlbumVo(share.getAlbumTitle(), share.getAlbumSummary(),
                share.getAlbumDate(),
                album == null ? null : album.getPlace(),
                album == null ? null : album.getUserName(),
                share.getPhotoNum(), photos);
    }

    /**
     * One decorated photo, reachable only through a live share token.
     *
     * <p>Checking that the photo belongs to the shared album is the whole
     * point: without it the token would be a key to every album at once.
     */
    @Transactional(readOnly = true)
    public byte[] sharedPhoto(String token, Long albumPhotoId) {
        AlbumShare share = require(token);
        AlbumPhoto ap = albumPhotos.findByIdAndAlbumId(albumPhotoId, share.getAlbumId())
                .orElseThrow(() -> new ApiException(ErrorCode.PHOTO_NOT_FOUND,
                        "That photo is not in this album"));

        String path = storage.exists(ap.getCompositePath())
                ? ap.getCompositePath()
                : ap.getPhotoUrl();
        if (!storage.exists(path)) {
            throw new ApiException(ErrorCode.PHOTO_NOT_FOUND, "The file is missing on disk");
        }
        return storage.read(path);
    }

    /** The 1200x630 card image, rendered on first crawl and then served from disk. */
    @Transactional
    public byte[] ogImage(String token) {
        AlbumShare share = require(token);
        if (storage.exists(share.getOgImagePath())) {
            return storage.read(share.getOgImagePath());
        }

        List<byte[]> sources = new ArrayList<>();
        for (AlbumPhoto ap : albumPhotos.findByAlbumIdOrderByPositionAsc(share.getAlbumId())) {
            // Prefer the decorated version: the handwriting is what makes the
            // card look like theirs rather than a stock photo grid.
            String path = storage.exists(ap.getCompositePath())
                    ? ap.getCompositePath()
                    : ap.getPhotoUrl();
            if (storage.exists(path)) {
                sources.add(storage.read(path));
            }
            if (sources.size() == 4) {
                break;
            }
        }

        byte[] jpeg = collage.render(sources);
        share.setOgImagePath(storage.write("og/%s.jpg".formatted(share.getShareToken()), jpeg));
        return jpeg;
    }

    private AlbumShare require(String token) {
        AlbumShare share = shares.findByShareToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.SHARE_REVOKED,
                        "This link is no longer available"));
        if (share.getRevokeTime() != null) {
            throw new ApiException(ErrorCode.SHARE_REVOKED, "This link was revoked");
        }
        if (share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.SHARE_EXPIRED, "This link has expired");
        }
        return share;
    }

    private ShareLinkVo toLink(AlbumShare share, String baseUrl) {
        return new ShareLinkVo(share.getShareToken(),
                baseUrl + "/s/" + share.getShareToken(), share.getViewNum());
    }

    /** 24 bytes, base64url, 32 characters. Not the album id, on purpose. */
    private static String newToken() {
        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
