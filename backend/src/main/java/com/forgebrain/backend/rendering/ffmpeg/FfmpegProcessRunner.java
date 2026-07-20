package com.forgebrain.backend.rendering.ffmpeg;

import com.forgebrain.backend.config.RenderingConfig;
import com.forgebrain.backend.exceptions.RenderExecutionException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Shared {@code ffmpeg}/{@code ffprobe} process execution — extracted out of {@link
 * FfmpegRenderEngine} so {@link com.forgebrain.backend.services.VoiceServiceImpl} (which needs
 * to generate a silent-fallback audio track and measure real audio file durations) doesn't
 * duplicate the same start/read/timeout/exit-code handling.
 */
@Component
public class FfmpegProcessRunner {

    private static final long PROCESS_TIMEOUT_SECONDS = 120;

    private final RenderingConfig renderingConfig;

    public FfmpegProcessRunner(RenderingConfig renderingConfig) {
        this.renderingConfig = renderingConfig;
    }

    public String ffmpegPath() {
        return renderingConfig.ffmpegPath();
    }

    /**
     * Runs an {@code ffmpeg} (or {@code ffprobe}) command to completion in {@code
     * workingDirectory}. Throws {@link RenderExecutionException} if the process can't start,
     * times out, or exits non-zero.
     */
    public void run(List<String> command, Path workingDirectory) {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RenderExecutionException("Could not start '" + command.get(0)
                    + "'. Is it installed and on PATH? See backend/README.md's FFmpeg requirement note.", e);
        }

        String output;
        try (InputStream processOutput = process.getInputStream()) {
            output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RenderExecutionException("Failed to read output from '" + command.get(0) + "'.", e);
        }

        boolean finished;
        try {
            finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderExecutionException("'" + command.get(0) + "' execution was interrupted.", e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new RenderExecutionException(
                    "'" + command.get(0) + "' did not finish within " + PROCESS_TIMEOUT_SECONDS + "s.");
        }
        if (process.exitValue() != 0) {
            throw new RenderExecutionException("'" + command.get(0) + "' exited with code " + process.exitValue()
                    + ":\n" + tail(output, 4000));
        }
    }

    /**
     * Measures a real media file's duration in seconds via {@code ffprobe}. Used by {@link
     * com.forgebrain.backend.services.VoiceServiceImpl} when a real narration file is found, to
     * reconcile it against the storyboard's word-count estimate (see {@code
     * renderer/voice-spec.md} Section 4).
     */
    public double probeDurationSeconds(Path mediaFile, Path workingDirectory) {
        List<String> command = List.of(renderingConfig.ffprobePath(), "-v", "error",
                "-show_entries", "format=duration", "-of", "csv=p=0", mediaFile.toAbsolutePath().toString());
        Process process;
        try {
            process = new ProcessBuilder(command).directory(workingDirectory.toFile()).start();
        } catch (IOException e) {
            throw new RenderExecutionException("Could not start '" + renderingConfig.ffprobePath() + "'.", e);
        }

        String output;
        try (InputStream processOutput = process.getInputStream()) {
            output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RenderExecutionException("Failed to read ffprobe output for '" + mediaFile + "'.", e);
        }

        boolean finished;
        try {
            finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderExecutionException("ffprobe execution was interrupted.", e);
        }
        if (!finished || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new RenderExecutionException("ffprobe could not measure duration for '" + mediaFile + "'.");
        }

        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException e) {
            throw new RenderExecutionException(
                    "ffprobe returned a non-numeric duration for '" + mediaFile + "': '" + output + "'.", e);
        }
    }

    private static String tail(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(text.length() - maxLength);
    }
}
