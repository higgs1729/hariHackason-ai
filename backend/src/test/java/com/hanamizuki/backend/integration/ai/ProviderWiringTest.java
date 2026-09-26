package com.hanamizuki.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exactly one implementation of each AI interface is live, whichever provider
 * is selected.
 *
 * <p>This is the failure mode the switch introduced. Two beans matching is a
 * context that will not start; none matching is the same. Either way the app
 * is down before the first request, which at a demo is worse than any answer
 * the model could have given — and neither shows up in a unit test of the
 * classes themselves.
 *
 * <p>Both branches are started here rather than just the default, because the
 * one nobody boots is the one that is broken.
 */
class ProviderWiringTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles({"local", "dev"})
    class TheDefault {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private AlbumEnricher enricher;

        @Autowired
        private ShootHinter hinter;

        /** No property set at all has to resolve, or a fresh checkout will not run. */
        @Test
        void usesAnthropic() {
            assertThat(enricher).isInstanceOf(ClaudeAlbumEnricher.class);
            assertThat(hinter).isInstanceOf(ClaudeShootHinter.class);
            assertThat(context.getBeansOfType(AlbumEnricher.class)).hasSize(1);
            assertThat(context.getBeansOfType(ShootHinter.class)).hasSize(1);
        }
    }

    @Nested
    @SpringBootTest(properties = "app.ai.provider=openrouter")
    @ActiveProfiles({"local", "dev"})
    class Switched {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private AlbumEnricher enricher;

        @Autowired
        private ShootHinter hinter;

        @Test
        void usesOpenRouter() {
            assertThat(enricher).isInstanceOf(OpenRouterAlbumEnricher.class);
            assertThat(hinter).isInstanceOf(OpenRouterShootHinter.class);
            assertThat(context.getBeansOfType(AlbumEnricher.class)).hasSize(1);
            assertThat(context.getBeansOfType(ShootHinter.class)).hasSize(1);
        }

        /**
         * With no key the app still starts and both features report themselves
         * unavailable, which is what makes the callers fall back quietly rather
         * than fail. Same contract the Anthropic side has always had.
         */
        @Test
        void startsWithoutAKeyAndDegrades() {
            assertThat(enricher.isAvailable()).isFalse();
            assertThat(hinter.isAvailable()).isFalse();
        }

        /** album.aiModel must name what actually wrote the copy. */
        @Test
        void reportsTheModelItWouldUse() {
            assertThat(enricher.model()).isEqualTo("anthropic/claude-sonnet-5");
        }
    }
}
