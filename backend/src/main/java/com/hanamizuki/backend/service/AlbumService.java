package com.hanamizuki.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.AlbumMemberRepository;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.AlbumRepository;

/** Reading an album, and the membership check every album endpoint shares. */
@Service
public class AlbumService {

    private final AlbumRepository albums;
    private final AlbumMemberRepository members;
    private final AlbumPhotoRepository albumPhotos;

    public AlbumService(AlbumRepository albums, AlbumMemberRepository members,
                        AlbumPhotoRepository albumPhotos) {
        this.albums = albums;
        this.members = members;
        this.albumPhotos = albumPhotos;
    }

    @Transactional(readOnly = true)
    public AlbumVo get(Long albumId, Long userId) {
        Album album = albums.findById(albumId)
                .orElseThrow(() -> new ApiException(ErrorCode.ALBUM_NOT_FOUND));
        AlbumMember me = requireMember(albumId, userId);

        List<AlbumMember> allMembers = members.findByAlbumIdOrderByIdAsc(albumId);
        List<AlbumPhoto> photos = albumPhotos.findByAlbumIdOrderByPositionAsc(albumId);

        return AlbumVo.of(album, me.getMemberRole().name().toLowerCase(), allMembers, photos);
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
}
