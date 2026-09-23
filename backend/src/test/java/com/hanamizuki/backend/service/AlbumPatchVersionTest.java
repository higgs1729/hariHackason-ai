package com.hanamizuki.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.hanamizuki.backend.api.album.AlbumRequests.AlbumPatchRequest;
import com.hanamizuki.backend.api.album.AlbumVos.AlbumVo;
import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.enums.MemberRole;
import com.hanamizuki.backend.repository.AlbumMemberRepository;
import com.hanamizuki.backend.repository.AlbumRepository;

/**
 * Editing an album: the follow copy is rewritten, a stale version is refused,
 * and the version moves by exactly one per accepted write.
 *
 * <p><b>The version assertions do not guard the bug they were written for.</b>
 * Renaming an album also rewrites {@code album_member.albumTitle}, and that
 * bulk update clears the persistence context — so running it before the album
 * was flushed detached the entity, turned the save into a merge, and produced a
 * second flush. On MySQL that showed up as +2 for a title change and +1 for a
 * summary change, measured both ways round. On H2, which is what this test
 * runs against, both orderings give +1: it does not reproduce.
 *
 * <p>The assertions are kept because the behaviour they describe is the one we
 * want, and they would catch a coarser regression. They are documented as not
 * catching this one so that nobody reads a green build as proof the ordering is
 * still right — that check is {@code ver.py} against MySQL, or a reviewer
 * reading the comment in {@code AlbumService.patch}.
 *
 * <p>The other two tests do hold on H2 and are the reason the class earns its
 * runtime.
 */
@SpringBootTest
@ActiveProfiles({"local", "dev"})
class AlbumPatchVersionTest {

    private static final long USER_ID = 4242L;

    @Autowired
    private AlbumService albumService;

    @Autowired
    private AlbumRepository albums;

    @Autowired
    private AlbumMemberRepository members;

    private Long albumId;

    @BeforeEach
    void createAlbumWithOneMember() {
        Album album = new Album();
        album.setTitle("もとのタイトル");
        album.setUserId(USER_ID);
        album.setUserName("てすと");
        albums.save(album);
        albumId = album.getId();

        AlbumMember member = new AlbumMember();
        member.setAlbumId(albumId);
        member.setUserId(USER_ID);
        member.setMemberRole(MemberRole.OWNER);
        member.setAlbumTitle(album.getTitle());
        members.save(member);
    }

    @Test
    void aTitleChangeMovesTheVersionByExactlyOne() {
        int before = albums.findById(albumId).orElseThrow().getVersion();

        AlbumVo after = albumService.patch(albumId, USER_ID, before,
                new AlbumPatchRequest("あたらしいタイトル", null, null));

        assertThat(after.version()).isEqualTo(before + 1);
        assertThat(after.title()).isEqualTo("あたらしいタイトル");
    }

    /**
     * The comparison case: a summary edit touches no follow copy, so it never
     * had the extra flush. On MySQL these two differed; here they agree, which
     * is exactly why H2 cannot stand in for the real check.
     */
    @Test
    void anEditWithNoFollowCopyMovesItByOneToo() {
        int before = albums.findById(albumId).orElseThrow().getVersion();

        AlbumVo after = albumService.patch(albumId, USER_ID, before,
                new AlbumPatchRequest(null, null, "いちにちのまとめ"));

        assertThat(after.version()).isEqualTo(before + 1);
    }

    /** The reason the bulk update is there at all. */
    @Test
    void theTitleReachesTheMemberRowsThatCarryACopyOfIt() {
        int before = albums.findById(albumId).orElseThrow().getVersion();

        albumService.patch(albumId, USER_ID, before,
                new AlbumPatchRequest("みんなのアルバム", null, null));

        assertThat(members.findByAlbumIdOrderByIdAsc(albumId))
                .isNotEmpty()
                .allSatisfy(member ->
                        assertThat(member.getAlbumTitle()).isEqualTo("みんなのアルバム"));
    }

    /** A stale version is rejected before anything is written. */
    @Test
    void aStaleVersionChangesNothing() {
        int current = albums.findById(albumId).orElseThrow().getVersion();

        assertThat(
                org.assertj.core.api.Assertions.catchThrowable(() ->
                        albumService.patch(albumId, USER_ID, current + 7,
                                new AlbumPatchRequest("よこどり", null, null))))
                .isNotNull();

        Album unchanged = albums.findById(albumId).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("もとのタイトル");
        assertThat(unchanged.getVersion()).isEqualTo(current);
    }
}
