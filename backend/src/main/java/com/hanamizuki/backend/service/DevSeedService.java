package com.hanamizuki.backend.service;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hanamizuki.backend.domain.Album;
import com.hanamizuki.backend.domain.AlbumMember;
import com.hanamizuki.backend.domain.AlbumPhoto;
import com.hanamizuki.backend.domain.Friend;
import com.hanamizuki.backend.domain.Photo;
import com.hanamizuki.backend.domain.User;
import com.hanamizuki.backend.domain.enums.FriendStatus;
import com.hanamizuki.backend.domain.enums.MemberRole;
import com.hanamizuki.backend.domain.enums.TakenTimeSource;
import com.hanamizuki.backend.integration.storage.FileStorage;
import com.hanamizuki.backend.integration.storage.PlaceholderImages;
import com.hanamizuki.backend.repository.AlbumMemberRepository;
import com.hanamizuki.backend.repository.AlbumPhotoRepository;
import com.hanamizuki.backend.repository.AlbumRepository;
import com.hanamizuki.backend.repository.FriendRepository;
import com.hanamizuki.backend.repository.PhotoRepository;
import com.hanamizuki.backend.repository.UserRepository;

/**
 * Rebuilds a demo-ready database in one call.
 *
 * <p>Worth the hour it costs: recreating four accounts, their friendships and
 * two albums by hand, on stage, after an unplanned restart, is how a demo dies.
 *
 * <p>Runs only under the dev profile.
 */
@Service
@Profile("dev")
public class DevSeedService {

    /** Matches the credentials the frontend mock uses, so switching modes needs no retyping. */
    private static final String DEMO_PASSWORD = "password";

    private final UserRepository users;
    private final FriendRepository friends;
    private final PhotoRepository photos;
    private final AlbumRepository albums;
    private final AlbumMemberRepository albumMembers;
    private final AlbumPhotoRepository albumPhotos;
    private final PasswordEncoder passwordEncoder;
    private final FileStorage storage;

    public DevSeedService(UserRepository users, FriendRepository friends, PhotoRepository photos,
                          AlbumRepository albums, AlbumMemberRepository albumMembers,
                          AlbumPhotoRepository albumPhotos, PasswordEncoder passwordEncoder,
                          FileStorage storage) {
        this.users = users;
        this.friends = friends;
        this.photos = photos;
        this.albums = albums;
        this.albumMembers = albumMembers;
        this.albumPhotos = albumPhotos;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

    @Transactional
    public Map<String, Object> seed() {
        User nao = user("nao", "わたし");
        User ayaka = user("ayaka", "あやか");
        User miki = user("miki", "みき");
        User rin = user("rin", "りん");

        befriend(nao, ayaka);
        befriend(nao, miki);
        befriend(nao, rin);

        // Two afternoons, a day apart — which is exactly what the 30-minute
        // clustering rule is meant to split into two albums.
        LocalDateTime firstDay = LocalDateTime.now().minusDays(2).withHour(17).withMinute(30);
        LocalDateTime secondDay = LocalDateTime.now().minusDays(1).withHour(12).withMinute(0);

        List<Photo> afternoon = photos(nao, firstDay, 6, 0);
        List<Photo> lunch = photos(nao, secondDay, 4, 6);

        Album album1 = album(nao, "最高の1日", "テスト終わりの放課後、みんなで梅田へ。",
                "梅田", firstDay.toLocalDate(), afternoon, List.of(ayaka, miki));
        Album album2 = album(nao, "お昼のピース", "休みの日、駅前で集合。",
                "難波", secondDay.toLocalDate(), lunch, List.of(rin));

        return Map.of(
                "users", List.of(summary(nao), summary(ayaka), summary(miki), summary(rin)),
                "password", DEMO_PASSWORD,
                "albumIds", List.of(album1.getId(), album2.getId()),
                "photoCount", afternoon.size() + lunch.size());
    }

    private User user(String account, String name) {
        return users.findByUserAccount(account).orElseGet(() -> {
            User user = new User();
            user.setUserAccount(account);
            user.setUserPassword(passwordEncoder.encode(DEMO_PASSWORD));
            user.setUserName(name);
            return users.save(user);
        });
    }

    /** Writes both directions, so the friend list stays a single-column query. */
    private void befriend(User a, User b) {
        friends.save(edge(a, b, a));
        friends.save(edge(b, a, a));
        a.setFriendNum(a.getFriendNum() + 1);
        b.setFriendNum(b.getFriendNum() + 1);
    }

    private Friend edge(User owner, User other, User requester) {
        Friend friend = new Friend();
        friend.setUserId(owner.getId());
        friend.setFriendId(other.getId());
        friend.setStatus(FriendStatus.ACCEPTED);
        friend.setRequestUserId(requester.getId());
        friend.setFriendUserName(other.getUserName());
        friend.setFriendUserAvatar(other.getUserAvatar());
        return friend;
    }

    /**
     * @param variant offsets the placeholder image, so the two groups do not
     *                render byte-identical pictures. They used to, which was
     *                harmless while sha256 was left null and a bug the moment
     *                it was not: two rows sharing a hash makes
     *                {@code findByUserIdAndSha256} — declared as returning one
     *                — throw on the next upload of that image.
     */
    private List<Photo> photos(User owner, LocalDateTime start, int count, int variant) {
        List<Photo> created = new ArrayList<>();
        for (int i = variant; i < variant + count; i++) {
            Photo photo = new Photo();
            photo.setUserId(owner.getId());
            photo.setUserName(owner.getUserName());
            photo.setPicName("seed-%d.jpg".formatted(i - variant));
            photo.setPicWidth(800);
            photo.setPicHeight(600);
            photo.setPicScale(800d / 600d);
            photo.setPicFormat("jpeg");
            // Minutes apart, so they land in one cluster rather than several.
            photo.setTakenTime(start.plusMinutes(7L * (i - variant)));
            photo.setTakenTimeSource(TakenTimeSource.EXIF);
            // The filename wants the id, but the id only exists after the
            // insert, and filePath is NOT NULL. So: insert with a placeholder,
            // then overwrite it — the row is managed at that point, so the
            // real path is flushed on commit without a second save() call.
            photo.setFilePath("");
            photos.save(photo);

            byte[] jpeg = PlaceholderImages.jpeg(800, 600, i);
            photo.setPicSize((long) jpeg.length);
            // Seeded rows were leaving this null, which meant they could never
            // match a re-upload — seed data behaving differently from uploaded
            // data is exactly the kind of difference that hides a bug until
            // the demo. Same digest the upload path computes.
            photo.setSha256(sha256(jpeg));
            photo.setFilePath(storage.write("photos/seed/%d.jpg".formatted(photo.getId()), jpeg));
            photo.setThumbPath(storage.write("thumbs/seed/%d.jpg".formatted(photo.getId()),
                    PlaceholderImages.jpeg(400, 300, i)));
            created.add(photo);
        }
        owner.setPhotoNum(owner.getPhotoNum() + count);
        return created;
    }

    private Album album(User owner, String title, String summary, String place,
                        LocalDate date, List<Photo> pictures, List<User> guests) {
        Album album = new Album();
        album.setTitle(title);
        album.setSummary(summary);
        album.setPlace(place);
        album.setAlbumDate(date);
        album.setAiGenerated(false);
        album.setUserId(owner.getId());
        album.setUserName(owner.getUserName());
        album.setPhotoNum(pictures.size());
        album.setMemberNum(guests.size() + 1);
        Photo cover = pictures.get(0);
        album.setCoverPhotoId(cover.getId());
        album.setCoverPhotoUrl(cover.getFilePath());
        album.setCoverThumbUrl(cover.getThumbPath());
        albums.save(album);

        member(album, owner, MemberRole.OWNER);
        guests.forEach(guest -> member(album, guest, MemberRole.EDITOR));

        String[] captions = {"放課後", "梅田", "みんなで", "夕日", "帰り道", "ピース"};
        for (int i = 0; i < pictures.size(); i++) {
            Photo photo = pictures.get(i);
            AlbumPhoto ap = new AlbumPhoto();
            ap.setAlbumId(album.getId());
            ap.setPhotoId(photo.getId());
            ap.setPosition(i);
            // Copied in rather than joined later — this is what lets the album
            // screen read two tables and no more.
            ap.setPhotoUrl(photo.getFilePath());
            ap.setThumbUrl(photo.getThumbPath());
            ap.setPicWidth(photo.getPicWidth());
            ap.setPicHeight(photo.getPicHeight());
            ap.setPicScale(photo.getPicScale());
            ap.setTakenTime(photo.getTakenTime());
            ap.setCaption(captions[i % captions.length]);
            ap.setPlace(place);
            ap.setWeather("晴れ");
            albumPhotos.save(ap);

            photo.setAlbumNum(photo.getAlbumNum() + 1);
        }

        owner.setAlbumNum(owner.getAlbumNum() + 1);
        guests.forEach(guest -> guest.setAlbumNum(guest.getAlbumNum() + 1));
        return album;
    }

    private void member(Album album, User user, MemberRole role) {
        AlbumMember member = new AlbumMember();
        member.setAlbumId(album.getId());
        member.setUserId(user.getId());
        member.setMemberRole(role);
        member.setUserName(user.getUserName());
        member.setUserAvatar(user.getUserAvatar());
        member.setAlbumTitle(album.getTitle());
        albumMembers.save(member);
    }

    private Map<String, Object> summary(User user) {
        return Map.of("id", user.getId(), "userAccount", user.getUserAccount(),
                "userName", user.getUserName());
    }

    /** The same digest {@code PhotoService} computes, so seeded rows dedup like real ones. */
    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }
}
