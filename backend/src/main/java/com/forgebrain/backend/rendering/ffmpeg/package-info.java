/**
 * The real, local, deterministic execution path from a validated {@link
 * com.forgebrain.backend.rendering.RenderPlan} to a playable MP4 file: {@link
 * com.forgebrain.backend.rendering.ffmpeg.FfmpegRenderEngine} orchestrates {@link
 * com.forgebrain.backend.rendering.ffmpeg.PlaceholderAssetResolver} (placeholder-safe asset
 * resolution), {@link com.forgebrain.backend.rendering.ffmpeg.SrtWriter} (subtitle burn-in
 * file), and {@link com.forgebrain.backend.rendering.ffmpeg.RenderCommandBuilder} (pure FFmpeg
 * argument construction) around an actual {@code ffmpeg} process invocation. Kept separate from
 * {@link com.forgebrain.backend.rendering}, which holds the planning artifacts (`RenderPlan`
 * and friends) that have no dependency on FFmpeg or any other specific rendering technology.
 */
package com.forgebrain.backend.rendering.ffmpeg;
