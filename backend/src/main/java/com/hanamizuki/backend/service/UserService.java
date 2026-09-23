package com.hanamizuki.backend.service;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.api.auth.AuthDtos.UserDto;
import com.hanamizuki.backend.api.user.UserRequests.PatchMeRequest;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.repository.AlbumMemberRepository;
import com.hanamizuki.backend.repository.AlbumRepository;
import com.hanamizuki.backend.repository.FriendRepository;
import com.hanamizuki.backend.repository.PhotoRepository;
import com.hanamizuki.backend.repository.UserRepository;

/**
 * ユーザー情報。/ 用户中心。
 *
 * <p>Almost all of this class is one method, and almost all of that method is
 * the cost of the denormalisation policy: a display name is copied onto eight
 * tables, and renaming has to know which of those copies follow and which are
 * deliberately frozen.
 */
@Service
public class UserService {

    private final UserRepository users;
    private final FriendRepository friends;
    private final AlbumRepository albums;
    private final AlbumMemberRepository members;
    private final PhotoRepository photos;

    public UserService(UserRepository users, FriendRepository friends,
                       AlbumRepository albums, AlbumMemberRepository members,
                       PhotoRepository photos) {
        this.users = users;
        this.friends = friends;
        this.albums = albums;
        this.members = members;
        this.photos = photos;
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.of(require(userId));
    }

    /**
     * Any user is readable by id. The type is {@link UserDto}, which has no
     * field for the password hash, so "which columns are safe here" is not a
     * question this method has to answer correctly every time.
     */
    @Transactional(readOnly = true)
    public UserDto get(Long userId) {
        return UserDto.of(require(userId));
    }

    /**
     * 友だち検索。/ 搜索好友。
     *
     * <p>A blank query returns nothing rather than everyone: an empty search box
     * should not be a user directory.
     */
    @Transactional(readOnly = true)
    public List<UserDto> search(String query, Long selfId) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) {
            return List.of();
        }
        return users.search(q, selfId, Limit.of(20)).stream().map(UserDto::of).toList();
    }

    /**
     * 名前とプロフィールの変更。/ 修改昵称与简介。
     *
     * <p>The name is copied onto eight tables, and the DDL marks each copy as
     * either following the source or a snapshot. Only the four followers are
     * rewritten here:
     *
     * <ul>
     *   <li>follow — {@code friend.friendUserName}, {@code photo.userName},
     *       {@code album.userName}, {@code album_member.userName}
     *   <li>snapshot — {@code block.blockedUserName},
     *       {@code album_photo.overlayUserName},
     *       {@code capsule_recipient.userName}, {@code notification.fromUserName}
     * </ul>
     *
     * <p>Updating all eight would be the more obvious code and the wrong
     * behaviour: the snapshots record who did something at the time it
     * happened, and a notification that reads "あやか added a photo" should keep
     * saying あやか even after that account is renamed.
     */
    @Transactional
    public UserDto patchMe(Long userId, PatchMeRequest request) {
        User user = require(userId);

        if (request.userProfile() != null) {
            String profile = request.userProfile().strip();
            if (profile.length() > 512) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "userProfile has to be at most 512 characters");
            }
            user.setUserProfile(profile.isEmpty() ? null : profile);
        }

        if (request.userName() != null) {
            String name = request.userName().strip();
            if (name.isEmpty() || name.length() > 256) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "userName has to be 1-256 characters");
            }
            boolean changed = !name.equals(user.getUserName());
            user.setUserName(name);
            if (changed) {
                propagateName(user);
            }
        }
        return UserDto.of(user);
    }

    /**
     * Four bulk updates rather than loading the rows: a user with fifty photos
     * would otherwise be fifty selects and fifty updates for a rename nobody
     * is waiting on.
     *
     * <p>Same transaction as the rename itself, so the copies cannot be left
     * pointing at a name the source no longer has.
     */
    private void propagateName(User user) {
        friends.renameFriend(user.getId(), user.getUserName(), user.getUserAvatar());
        members.renameMember(user.getId(), user.getUserName(), user.getUserAvatar());
        photos.renameUploader(user.getId(), user.getUserName());
        albums.renameCreator(user.getId(), user.getUserName());
    }

    private User require(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }
}
