package com.hanamizuki.backend.integration.ai;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs the Claude Code CLI ({@code claude -p}) on this machine as the AI step.
 *
 * <p>The demo runs on one PC that is already signed in to Claude Code, so the
 * CLI is the AI with no key to provision. It is slower to start than an HTTP
 * call (a few seconds of process start-up), which is why its timeouts are
 * separate from the API provider's.
 *
 * <p>Isolation from the developer's own setup is deliberate: no MCP servers,
 * no settings files, no CLAUDE.md, only the tools a call names. Otherwise every
 * call would pay for connecting a dozen servers and would read instructions
 * that were written for a person, not for this prompt.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cli", matchIfMissing = true)
public class ClaudeCli {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCli.class);

    private final String command;
    private final String model;
    private final ObjectMapper objectMapper;

    public ClaudeCli(@Value("${app.ai.cli.command}") String command,
                     @Value("${app.ai.cli.model}") String model,
                     ObjectMapper objectMapper) {
        this.command = command;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    /** The binary is there and runnable. Says nothing about whether it is signed in. */
    public boolean isAvailable() {
        return Files.isExecutable(Path.of(command));
    }

    /** What goes into {@code album.aiModel}, so a row says which path wrote it. */
    public String modelLabel() {
        return "claude-cli:" + model;
    }

    /**
     * Sends one prompt and returns the schema-validated JSON.
     *
     * @param workDir  the only directory the {@code Read} tool may see; null for no tools
     * @param tools    built-in tool names, e.g. {@code "Read"}; empty for none
     * @throws RuntimeException on a timeout, a non-zero exit, or a result without
     *                          structured output; callers degrade on any of them
     */
    public JsonNode run(String systemPrompt, String prompt, String jsonSchema,
                        Path workDir, List<String> tools, Duration timeout) {
        List<String> args = new ArrayList<>(List.of(
                command, "-p",
                "--output-format", "json",
                "--model", model,
                "--strict-mcp-config",
                "--setting-sources", "",
                "--no-session-persistence",
                "--system-prompt", systemPrompt,
                "--json-schema", jsonSchema,
                "--tools", String.join(",", tools)));
        if (!tools.isEmpty()) {
            args.add("--allowedTools");
            args.add(String.join(",", tools));
        }

        ProcessBuilder builder = new ProcessBuilder(args.stream().map(ClaudeCli::windowsArg).toList());
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        builder.redirectErrorStream(true);
        // Started from inside a Claude Code session (as it is during
        // development), the child would otherwise think it is nested in one.
        builder.environment().keySet().removeIf(k -> k.startsWith("CLAUDECODE") || k.startsWith("CLAUDE_CODE_"));

        long started = System.nanoTime();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start " + command, e);
        }
        CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            process.destroyForcibly();
            throw new IllegalStateException("Could not write the prompt", e);
        }

        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new IllegalStateException("claude -p timed out after " + timeout.toSeconds() + "s");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for claude -p", e);
        }

        String text = new String(output.join(), StandardCharsets.UTF_8);
        long ms = (System.nanoTime() - started) / 1_000_000;
        if (process.exitValue() != 0) {
            throw new IllegalStateException("claude -p exited " + process.exitValue() + ": " + tail(text));
        }
        JsonNode result;
        try {
            result = objectMapper.readTree(text);
        } catch (RuntimeException e) {
            throw new IllegalStateException("claude -p printed something that is not JSON: " + tail(text), e);
        }
        if (result.path("is_error").asBoolean(false)) {
            throw new IllegalStateException("claude -p reported an error: " + result.path("result").asString(""));
        }
        JsonNode structured = result.get("structured_output");
        if (structured == null || !structured.isObject()) {
            throw new IllegalStateException("claude -p returned no structured output");
        }
        log.info("claude -p answered in {} ms ({} turns, ${})", ms,
                result.path("num_turns").asInt(), result.path("total_cost_usd").asDouble());
        return structured;
    }

    /**
     * Quotes one argument for CreateProcess. Java only adds quotes around
     * arguments with spaces and leaves embedded quotes alone, which splits a
     * JSON schema into pieces. An argument that is already quoted is passed
     * through untouched, so quoting it here is enough.
     */
    static String windowsArg(String arg) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return arg;
        }
        if (arg.isEmpty()) {
            return "\"\"";
        }
        if (arg.chars().noneMatch(c -> c == ' ' || c == '"' || c == '\t' || c == '\n' || c == '\r')) {
            return arg;
        }
        StringBuilder out = new StringBuilder("\"");
        int backslashes = 0;
        for (char c : arg.toCharArray()) {
            if (c == '\\') {
                backslashes++;
                continue;
            }
            if (c == '"') {
                out.append("\\".repeat(backslashes * 2 + 1));
            } else {
                out.append("\\".repeat(backslashes));
            }
            backslashes = 0;
            out.append(c);
        }
        out.append("\\".repeat(backslashes * 2)).append('"');
        return out.toString();
    }

    private static byte[] readAll(InputStream in) {
        try (in; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            in.transferTo(buffer);
            return buffer.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static String tail(String text) {
        return text.length() <= 500 ? text : text.substring(text.length() - 500);
    }
}
