package com.hanamizuki.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.hanamizuki.backend.api.hint.HintVos.ShootHintRequest;
import com.hanamizuki.backend.api.hint.HintVos.ShootHintVo;
import com.hanamizuki.backend.common.RateLimiter;
import com.hanamizuki.backend.error.ApiException;
import com.hanamizuki.backend.error.ErrorCode;
import com.hanamizuki.backend.integration.ai.ShootHint;
import com.hanamizuki.backend.integration.ai.ShootHinter;

/**
 * The shoot hint's whole contract is that it never fails.
 *
 * <p>It sits on the camera screen with a group posing in front of it, and
 * 05-backend-answers section 3.1 is explicit: a 503 here turns the AI moment
 * into the moment the AI fell over. So every way the model can let us down gets
 * its own case, and all of them assert the same two things — text came back,
 * and nothing was thrown.
 *
 * <p>The cases that matter most are the ones that are not exceptions. A
 * timeout is easy to remember; a model that answers with an empty pose list,
 * or with the word "null", is the one that reaches the screen looking fine and
 * renders as a blank panel.
 */
class ShootHintServiceTest {

    /** Stands in for Claude. The interface exists so this can. */
    private record FakeHinter(boolean available, Supplier<ShootHint> answer) implements ShootHinter {

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public ShootHint suggest(int memberCount, List<String> names, String place, String mood) {
            return answer.get();
        }
    }

    private static ShootHintService serviceReturning(ShootHint hint) {
        return service(new FakeHinter(true, () -> hint));
    }

    private static ShootHintService service(ShootHinter hinter) {
        return new ShootHintService(hinter, new RateLimiter(), 100, 60);
    }

    private static ShootHintVo ask(ShootHintService service, int memberCount) {
        return service.suggest(
                new ShootHintRequest(memberCount, List.of("なお", "あやか"), "教室", "たのしい"),
                1L);
    }

    private static void assertUsable(ShootHintVo vo) {
        assertThat(vo).isNotNull();
        assertThat(vo.hint()).isNotNull().isNotBlank();
        assertThat(vo.poses()).isNotEmpty().allSatisfy(pose ->
                assertThat(pose).isNotBlank().hasSizeLessThanOrEqualTo(20));
    }

    @Test
    void noApiKeyStillAnswers() {
        ShootHintVo vo = ask(service(new FakeHinter(false, () -> {
            throw new AssertionError("must not be called without a key");
        })), 3);

        assertUsable(vo);
        assertThat(vo.aiGenerated()).isZero();
    }

    @Test
    void aThrownExceptionStillAnswers() {
        ShootHintVo vo = ask(service(new FakeHinter(true, () -> {
            throw new IllegalStateException("connection reset");
        })), 3);

        assertUsable(vo);
        assertThat(vo.aiGenerated()).isZero();
    }

    /**
     * Each of these is a 200 from the model carrying nothing usable. They do
     * not throw, so without this they would reach the screen as a blank panel.
     */
    @Test
    void anAnswerWithNothingInItStillAnswers() {
        List<ShootHint> useless = Arrays.asList(
                null,
                new ShootHint(null, List.of("ピース")),
                new ShootHint("   ", List.of("ピース")),
                new ShootHint("肩を寄せて", null),
                new ShootHint("肩を寄せて", List.of()),
                new ShootHint("肩を寄せて", Arrays.asList(null, "  ", null)));

        for (ShootHint hint : useless) {
            ShootHintVo vo = ask(serviceReturning(hint), 2);
            assertUsable(vo);
            assertThat(vo.aiGenerated()).as("%s", hint).isZero();
        }
    }

    @Test
    void aGoodAnswerIsPassedThrough() {
        ShootHintVo vo = ask(serviceReturning(
                new ShootHint("窓際に並んで、逆光で撮ろう", List.of("手をつなぐ", "上を見る"))), 2);

        assertThat(vo.hint()).isEqualTo("窓際に並んで、逆光で撮ろう");
        assertThat(vo.poses()).containsExactly("手をつなぐ", "上を見る");
        assertThat(vo.aiGenerated()).isOne();
    }

    @Test
    void tooManyPosesAreCutToThree() {
        ShootHintVo vo = ask(serviceReturning(new ShootHint("並ぼう",
                List.of("いち", "に", "さん", "し", "ご"))), 4);

        assertThat(vo.poses()).containsExactly("いち", "に", "さん");
    }

    @Test
    void anOverlongPoseIsTrimmedRatherThanDropped() {
        String essay = "あ".repeat(80);
        ShootHintVo vo = ask(serviceReturning(new ShootHint("並ぼう", List.of(essay))), 4);

        assertThat(vo.poses()).hasSize(1);
        assertThat(vo.poses().get(0)).hasSize(20);
        assertThat(vo.aiGenerated()).isOne();
    }

    /**
     * The canned text is what every demo shows until the key exists, so the
     * advice has to suit the group. "みんなで顔を寄せて" is wrong for one person
     * and impossible for eight.
     */
    @Test
    void theCannedTextDependsOnHowManyPeopleAreInShot() {
        ShootHintService service = service(new FakeHinter(false, () -> null));

        List<String> hints = List.of(
                ask(service, 1).hint(),
                ask(service, 2).hint(),
                ask(service, 4).hint(),
                ask(service, 8).hint());

        assertThat(hints).doesNotHaveDuplicates();
    }

    @Test
    void anAbsentCountIsTakenFromTheNames() {
        ShootHintService service = service(new FakeHinter(false, () -> null));

        ShootHintVo inferred = service.suggest(
                new ShootHintRequest(null, List.of("a", "b"), null, null), 1L);
        ShootHintVo explicit = service.suggest(
                new ShootHintRequest(2, List.of(), null, null), 1L);

        assertThat(inferred.hint()).isEqualTo(explicit.hint());
    }

    /** No count and no names is still a photo of somebody. */
    @Test
    void anEmptyRequestIsAnswered() {
        ShootHintVo vo = service(new FakeHinter(false, () -> null))
                .suggest(new ShootHintRequest(null, null, null, null), 1L);

        assertUsable(vo);
    }

    /**
     * The one case that is not a fallback. Canned text would be friendlier, but
     * it would also hide from the user that they are repeatedly triggering a
     * call that costs money.
     */
    @Test
    void theRateLimitIsTheOneRefusal() {
        ShootHintService service = new ShootHintService(
                new FakeHinter(false, () -> null), new RateLimiter(), 3, 60);

        for (int i = 0; i < 3; i++) {
            assertUsable(ask(service, 2));
        }

        ApiException refused = catchThrowableOfType(ApiException.class, () -> ask(service, 2));
        assertThat(refused).isNotNull();
        assertThat(refused.code()).isEqualTo(ErrorCode.RATE_LIMITED);
    }

    /** One user hitting the limit does not lock out anyone else. */
    @Test
    void theLimitIsPerUser() {
        ShootHintService service = new ShootHintService(
                new FakeHinter(false, () -> null), new RateLimiter(), 1, 60);
        ShootHintRequest request = new ShootHintRequest(2, List.of(), null, null);

        service.suggest(request, 1L);
        assertThat(catchThrowableOfType(ApiException.class,
                () -> service.suggest(request, 1L))).isNotNull();

        assertUsable(service.suggest(request, 2L));
    }
}
