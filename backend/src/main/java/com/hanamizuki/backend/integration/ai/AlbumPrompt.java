package com.hanamizuki.backend.integration.ai;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hanamizuki.backend.domain.Photo;

/**
 * What to ask for an album, and what to keep of the answer.
 *
 * <p>Shared by both providers. The wording is tuned Japanese and the checks
 * are the difference between a caption on the right photo and a caption on
 * someone else's, so a second copy would be a second thing to keep in step —
 * and the copy nobody edited would be the one that looked fine.
 */
final class AlbumPrompt {

    private static final Logger log = LoggerFactory.getLogger(AlbumPrompt.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> WEATHER = Set.of("晴れ", "曇り", "雨", "雪");
    private static final int CAPTION_MAX = 10;
    private static final int COMMENT_MAX = 255;

    static final String SYSTEM = """
            あなたは高校生の写真アルバムに言葉を添える編集者です。
            渡された写真は同じ日の同じ時間帯に撮られたものです。

            - タイトルは10文字以内、日本語、その日の気分が伝わるもの
            - caption は2〜5文字の短いラベル（例:「放課後」「夕日」）
            - weather は 晴れ / 曇り / 雨 / 雪 のいずれか。判断できなければ null
            - place は看板や風景から推測できる場合のみ。できなければ null
            - comment は友達に話しかけるような1文
            - 事実を作らないこと。写真から読み取れないことは null にする
            """;

    private AlbumPrompt() {
    }

    /** Ids and timestamps, so the model can tie its answers back to photos. */
    static String describe(List<Photo> cluster) {
        StringBuilder text = new StringBuilder("写真一覧:\n");
        for (Photo photo : cluster) {
            text.append("- photoId=").append(photo.getId())
                    .append(" 撮影=").append(photo.getTakenTime().format(STAMP));
            if (photo.getLatitude() != null) {
                text.append(" 位置=").append(photo.getLatitude())
                        .append(',').append(photo.getLongitude());
            }
            text.append('\n');
        }
        text.append("\n画像は上の順番と同じです。この日のアルバムを作ってください。");
        return text.toString();
    }

    /**
     * Never trusts the ids that come back.
     *
     * <p>A hallucinated {@code photoId} would attach a caption to someone
     * else's photo, and an out-of-range {@code coverPhotoId} would leave the
     * album with a broken cover. Both are cheap to check and expensive to miss.
     */
    static AlbumDraft validate(AlbumDraft draft, List<Photo> cluster) {
        Set<Long> known = cluster.stream().map(Photo::getId).collect(Collectors.toSet());

        List<PhotoInsight> kept = new ArrayList<>();
        for (PhotoInsight insight : draft.photos() == null ? List.<PhotoInsight>of() : draft.photos()) {
            if (insight.photoId() == null || !known.contains(insight.photoId())) {
                log.warn("Dropping insight for unknown photoId {}", insight.photoId());
                continue;
            }
            kept.add(new PhotoInsight(insight.photoId(),
                    clip(insight.caption(), CAPTION_MAX),
                    clip(insight.place(), 64),
                    WEATHER.contains(insight.weather()) ? insight.weather() : null,
                    clip(insight.comment(), COMMENT_MAX)));
        }

        Long cover = known.contains(draft.coverPhotoId())
                ? draft.coverPhotoId()
                : cluster.get(0).getId();
        String title = draft.title() == null || draft.title().isBlank() ? null : draft.title();

        return new AlbumDraft(title, cover, clip(draft.summary(), 255), kept);
    }

    static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
