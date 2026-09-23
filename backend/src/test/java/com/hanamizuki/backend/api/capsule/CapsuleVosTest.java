package com.hanamizuki.backend.api.capsule;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleOpenedVo;
import com.hanamizuki.backend.api.capsule.CapsuleVos.CapsuleSealedVo;

/**
 * The capsule's one rule, asserted against the type rather than the behaviour.
 *
 * <p>A test that seals a capsule and checks the response omits the message
 * would pass while still leaving someone free to add the field back and null
 * it out — and then the rule holds only as long as every code path remembers
 * to. This checks the sealed shape has nowhere to put a message at all, which
 * is what actually makes the leak impossible.
 */
class CapsuleVosTest {

    private static List<String> componentsOf(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    @Test
    void aSealedCapsuleHasNowhereToPutTheMessageOrTheAlbum() {
        assertThat(componentsOf(CapsuleSealedVo.class))
                .doesNotContain("capsuleMsg", "album", "coverPhotoUrl", "albumTitle")
                .containsExactlyInAnyOrder("id", "status", "openTime", "daysRemaining");
    }

    @Test
    void anOpenedCapsuleCarriesBoth() {
        assertThat(componentsOf(CapsuleOpenedVo.class))
                .contains("capsuleMsg", "album", "openedTime");
    }

    /**
     * Both shapes implement a sealed interface, so a third one cannot be
     * introduced elsewhere without this file knowing about it.
     */
    @Test
    void thereAreExactlyTwoShapes() {
        assertThat(CapsuleVos.CapsuleVo.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(CapsuleSealedVo.class, CapsuleOpenedVo.class);
    }
}
