package com.hanamizuki.backend.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hanamizuki.backend.api.hint.HintVos.ShootHintRequest;
import com.hanamizuki.backend.api.hint.HintVos.ShootHintVo;
import com.hanamizuki.backend.common.RateLimiter;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.integration.ai.ShootHint;
import com.hanamizuki.backend.integration.ai.ShootHinter;

/**
 * プリクラ AI 指示。/ 拍照 AI 指示。
 *
 * <p>Stateless: no table, no job, one Claude call. The only rule that matters
 * is that this endpoint never fails. A 503 on the camera screen would turn the
 * AI moment into the moment the AI fell over, so every failure path below ends
 * in canned text and a 200.
 */
@Service
public class ShootHintService {

    private static final Logger log = LoggerFactory.getLogger(ShootHintService.class);

    /** Bounds on what goes into a prompt, and on what comes back out. */
    private static final int MAX_MEMBERS = 20;
    private static final int NAME_MAX = 32;
    private static final int FREE_TEXT_MAX = 64;
    private static final int HINT_MAX = 60;
    private static final int POSE_MAX = 20;
    private static final int POSES_MAX = 3;

    private final ShootHinter hinter;
    private final RateLimiter rateLimiter;
    private final int limit;
    private final Duration window;

    public ShootHintService(ShootHinter hinter, RateLimiter rateLimiter,
                            @Value("${app.ai.hint-rate-limit}") int limit,
                            @Value("${app.ai.hint-rate-window-seconds}") long windowSeconds) {
        this.hinter = hinter;
        this.rateLimiter = rateLimiter;
        this.limit = limit;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public ShootHintVo suggest(ShootHintRequest request, Long userId) {
        // The one case that is a real error rather than a fallback. Canned text
        // would be the friendlier answer, but it would also hide from the user
        // that they are hammering a route that costs money per call.
        if (!rateLimiter.tryAcquire("hint:" + userId, limit, window)) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "少し待ってからもう一度どうぞ");
        }

        int memberCount = memberCount(request);
        List<String> names = names(request);
        String place = clip(request.place(), FREE_TEXT_MAX);
        String mood = clip(request.mood(), FREE_TEXT_MAX);

        if (!hinter.isAvailable()) {
            return fallback(memberCount);
        }
        try {
            ShootHint hint = hinter.suggest(memberCount, names, place, mood);
            ShootHintVo vo = validate(hint);
            return vo == null ? fallback(memberCount) : vo;
        } catch (RuntimeException e) {
            // Warn, not error: this is a handled outcome with a defined
            // behaviour, and it will fire on every call until the key exists.
            log.warn("Shoot hint fell back to canned text: {}", e.toString());
            return fallback(memberCount);
        }
    }

    /**
     * @return null when the model returned something unusable, which is
     *         treated exactly like a transport failure
     */
    private ShootHintVo validate(ShootHint hint) {
        if (hint == null) {
            return null;
        }
        String text = clip(hint.hint(), HINT_MAX);
        if (text == null) {
            return null;
        }
        List<String> poses = new ArrayList<>();
        for (String pose : hint.poses() == null ? List.<String>of() : hint.poses()) {
            String trimmed = clip(pose, POSE_MAX);
            if (trimmed != null && poses.size() < POSES_MAX) {
                poses.add(trimmed);
            }
        }
        // A hint with no poses renders as an empty list on the screen. Better
        // to use the canned set, which always has three.
        if (poses.isEmpty()) {
            return null;
        }
        return new ShootHintVo(text, poses, 1);
    }

    /**
     * 固定文言。/ 兜底文案。
     *
     * <p>Written to be usable rather than to be a placeholder: with no API key
     * configured this is what every demo shows, and it is the difference
     * between a feature that degrades and a feature that is visibly broken.
     *
     * <p>Keyed on how many people are in shot, because that is the one input
     * that changes the advice. "みんなで顔を寄せて" is wrong for one person and
     * impossible for eight.
     */
    private static ShootHintVo fallback(int memberCount) {
        if (memberCount <= 1) {
            return new ShootHintVo(
                    "腕をいっぱいに伸ばして、少し上から撮ろう",
                    List.of("ピースを顔の横に", "横を向いて笑う", "目を閉じて上を向く"), 0);
        }
        if (memberCount == 2) {
            return new ShootHintVo(
                    "肩を寄せて、カメラは少し上から構えよう",
                    List.of("ほっぺをくっつける", "背中合わせ", "ハートを半分ずつ"), 0);
        }
        if (memberCount <= 4) {
            return new ShootHintVo(
                    "前後に少しずらして並ぶと、全員の顔が入るよ",
                    List.of("しゃがむ人と立つ人", "手を重ねて真ん中に", "全員でジャンプ"), 0);
        }
        return new ShootHintVo(
                "後ろの人は一歩高い場所に立つと、みんな写るよ",
                List.of("前列はしゃがむ", "端の人は内側を向く", "全員で同じポーズ"), 0);
    }

    /**
     * The count drives the fallback and the prompt, so it is never left to
     * whatever the client sent. An absent count is inferred from the names, and
     * a party of four hundred is clamped rather than refused.
     */
    private static int memberCount(ShootHintRequest request) {
        int count = request.memberCount() != null
                ? request.memberCount()
                : (request.memberNames() == null ? 0 : request.memberNames().size());
        return Math.min(Math.max(count, 1), MAX_MEMBERS);
    }

    /** Clipped and capped before it reaches a prompt, not after. */
    private static List<String> names(ShootHintRequest request) {
        if (request.memberNames() == null) {
            return List.of();
        }
        return request.memberNames().stream()
                .map(name -> clip(name, NAME_MAX))
                .filter(java.util.Objects::nonNull)
                .limit(MAX_MEMBERS)
                .toList();
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.strip();
        return text.length() <= max ? text : text.substring(0, max);
    }
}
