package com.hanamizuki.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The JSON schema travels as one command-line argument. If its quotes are not
 * escaped the CLI receives a dozen fragments and every AI call falls back.
 */
@EnabledOnOs(OS.WINDOWS)
class ClaudeCliTest {

    @Test
    void plainArgumentsPassThrough() {
        assertThat(ClaudeCli.windowsArg("--model")).isEqualTo("--model");
    }

    @Test
    void emptyArgumentStaysAnArgument() {
        assertThat(ClaudeCli.windowsArg("")).isEqualTo("\"\"");
    }

    @Test
    void quotesAreEscapedInsideOneQuotedArgument() {
        assertThat(ClaudeCli.windowsArg("{\"a\":\"b c\"}")).isEqualTo("\"{\\\"a\\\":\\\"b c\\\"}\"");
    }

    @Test
    void trailingBackslashesAreDoubledBeforeTheClosingQuote() {
        assertThat(ClaudeCli.windowsArg("C:\\a b\\")).isEqualTo("\"C:\\a b\\\\\"");
    }
}
