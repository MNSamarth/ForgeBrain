# Renderer Layer Examples

Worked examples for Voice Generation, Subtitle Generation, Asset Management, and the Renderer, threaded through **one topic — `java-arrays-basics` (the revision case)** — so the full production layer can be seen operating on one consistent input, continuing directly from the storyboard in [`brain/storyboard-examples.md`](../brain/storyboard-examples.md) Section 4.

**Scope note:** unlike `brain/`'s worked examples (which were generated programmatically and verified field-by-field), these examples are hand-authored and internally consistent by construction, not machine-verified against the source storyboard's exact numbers. This is a deliberate scope reduction for this consolidated architecture pass — see `TODO.md` for programmatic example generation as follow-up work, following the same pattern established in `brain/script-examples.md` and `brain/storyboard-examples.md`.

Source storyboard scenes (from `brain/storyboard-examples.md`): `scene-1-hook` (2.8s), `scene-2-rewind` (3.2s), `scene-3-the-fix` (5.6s), `scene-4-crash-resolved` (7.6s), `scene-5-recap` (6.0s), `scene-6-cta` (4.0s) — 29.2s estimated total.

## 1. Voice Generation

```json
{
  "topic_id": "java-arrays-basics",
  "topic_title": "Arrays Basics",
  "voice_profile": { "voice_id": "en-US-Neural2-D", "language_code": "en-US", "speaking_rate": 1.05, "pitch": -1.0 },
  "scenes": [
    { "scene_id": "scene-1-hook", "audio_file_uri": "gs://forgebrain-media/java-arrays-basics/voice/scene-1-hook.wav", "estimated_duration_seconds": 2.8, "actual_duration_seconds": 3.1, "duration_drift_seconds": 0.3, "word_timings": [
      { "word": "This", "start_time": 0.0, "end_time": 0.18 },
      { "word": "line", "start_time": 0.18, "end_time": 0.42 },
      { "word": "just", "start_time": 0.42, "end_time": 0.6 },
      { "word": "crashed", "start_time": 0.6, "end_time": 1.05 },
      { "word": "the", "start_time": 1.05, "end_time": 1.15 },
      { "word": "entire", "start_time": 1.15, "end_time": 1.55 },
      { "word": "program.", "start_time": 1.55, "end_time": 2.2 }
    ]},
    { "scene_id": "scene-2-rewind", "audio_file_uri": "gs://forgebrain-media/java-arrays-basics/voice/scene-2-rewind.wav", "estimated_duration_seconds": 3.2, "actual_duration_seconds": 3.0, "duration_drift_seconds": -0.2, "word_timings": [] },
    { "scene_id": "scene-3-the-fix", "audio_file_uri": "gs://forgebrain-media/java-arrays-basics/voice/scene-3-the-fix.wav", "estimated_duration_seconds": 5.6, "actual_duration_seconds": 5.9, "duration_drift_seconds": 0.3, "word_timings": [] },
    { "scene_id": "scene-4-crash-resolved", "audio_file_uri": "gs://forgebrain-media/java-arrays-basics/voice/scene-4-crash-resolved.wav", "estimated_duration_seconds": 7.6, "actual_duration_seconds": 7.4, "duration_drift_seconds": -0.2, "word_timings": [] },
    { "scene_id": "scene-5-recap", "audio_file_uri": "gs://forgebrain-media/java-arrays-basics/voice/scene-5-recap.wav", "estimated_duration_seconds": 6.0, "actual_duration_seconds": 6.3, "duration_drift_seconds": 0.3, "word_timings": [] },
    { "scene_id": "scene-6-cta", "audio_file_uri": "gs://forgebrain-media/java-arrays-basics/voice/scene-6-cta.wav", "estimated_duration_seconds": 4.0, "actual_duration_seconds": 3.8, "duration_drift_seconds": -0.2, "word_timings": [] }
  ],
  "total_estimated_duration_seconds": 29.2,
  "total_actual_duration_seconds": 29.5,
  "total_duration_drift_seconds": 0.3,
  "drift_exceeds_threshold": false,
  "drift_threshold_seconds": 3.0,
  "audio_format": "audio/wav",
  "sample_rate_hz": 24000,
  "confidence_notes": {
    "overall_confidence": "high",
    "flagged_uncertainties": [
      "Only scene-1-hook returned word-level timing from the provider in this run; the remaining scenes fall back to proportional-estimate reconciliation in Subtitle Generation."
    ],
    "unresolved_conflicts": []
  },
  "voice_version": "1.0.0",
  "generated_at": "2026-07-25T09:00:00Z",
  "based_on_storyboard_version": "1.0.0"
}
```

## 2. Subtitle Generation

```json
{
  "topic_id": "java-arrays-basics",
  "topic_title": "Arrays Basics",
  "subtitle_style": "boxed-caption",
  "format": "vtt",
  "subtitle_file_uri": "gs://forgebrain-media/java-arrays-basics/subtitles/captions.vtt",
  "safe_region": { "top_percent": 10, "bottom_percent": 18 },
  "scenes": [
    {
      "scene_id": "scene-1-hook",
      "reconciliation_method": "word-alignment",
      "segments": [
        { "text": "This line just crashed the entire program.", "start_time": 0.0, "end_time": 3.1, "emphasis_words": ["crashed"] }
      ]
    },
    {
      "scene_id": "scene-2-rewind",
      "reconciliation_method": "proportional-estimate",
      "segments": [
        { "text": "Five scores. Five separate variables. Already a mess.", "start_time": 3.1, "end_time": 6.1, "emphasis_words": [] }
      ]
    },
    {
      "scene_id": "scene-3-the-fix",
      "reconciliation_method": "proportional-estimate",
      "segments": [
        { "text": "One array replaces all five, each score reachable by its position, starting at zero.", "start_time": 6.1, "end_time": 12.0, "emphasis_words": ["one array", "zero"] }
      ]
    },
    {
      "scene_id": "scene-4-crash-resolved",
      "reconciliation_method": "proportional-estimate",
      "segments": [
        { "text": "But ask for a position that doesn't exist, and Java doesn't warn you, it crashes, right at that line.", "start_time": 12.0, "end_time": 19.4, "emphasis_words": ["crashes"] }
      ]
    },
    {
      "scene_id": "scene-5-recap",
      "reconciliation_method": "proportional-estimate",
      "segments": [
        { "text": "An array's size is fixed the moment it's created, know your bounds before you ask.", "start_time": 19.4, "end_time": 25.7, "emphasis_words": ["fixed", "bounds"] }
      ]
    },
    {
      "scene_id": "scene-6-cta",
      "reconciliation_method": "proportional-estimate",
      "segments": [
        { "text": "Ever hit this exact crash? Tell me in the comments.", "start_time": 25.7, "end_time": 29.5, "emphasis_words": [] }
      ]
    }
  ],
  "total_duration_seconds": 29.5,
  "confidence_notes": {
    "overall_confidence": "medium",
    "flagged_uncertainties": [
      "5 of 6 scenes used proportional-estimate reconciliation because word_timings was only available for scene-1-hook — timing accuracy for those scenes is best-effort, not frame-exact."
    ],
    "unresolved_conflicts": []
  },
  "subtitle_version": "1.0.0",
  "generated_at": "2026-07-25T09:05:00Z",
  "based_on_storyboard_version": "1.0.0",
  "based_on_voice_version": "1.0.0"
}
```

## 3. Asset Management

```json
{
  "topic_id": "java-arrays-basics",
  "topic_title": "Arrays Basics",
  "render_style": "dark-mode-ide",
  "resolved_theme": {
    "font_heading": "gs://forgebrain-assets/fonts/inter-bold.ttf",
    "font_body": "gs://forgebrain-assets/fonts/inter-regular.ttf",
    "font_code": "gs://forgebrain-assets/fonts/jetbrains-mono.ttf",
    "color_palette": { "background": "#0D1117", "text_primary": "#E6EDF3", "text_accent": "#58A6FF", "error": "#F85149", "success": "#3FB950" },
    "code_syntax_theme": "gs://forgebrain-assets/themes/dark-mode-ide-syntax.json"
  },
  "background_music": {
    "track_uri": "gs://forgebrain-assets/audio/minimal-tech-loop-04.mp3",
    "license": "royalty-free-internal-library",
    "volume_db": -22
  },
  "watermark": {
    "asset_uri": "gs://forgebrain-assets/brand/forgebrain-mark.png",
    "position": "bottom-right"
  },
  "scene_assets": [
    { "scene_id": "scene-4-crash-resolved", "asset_refs": ["gs://forgebrain-assets/icons/locker-row.svg"] }
  ],
  "confidence_notes": { "overall_confidence": "high", "flagged_uncertainties": [], "unresolved_conflicts": [] },
  "asset_manifest_version": "1.0.0",
  "generated_at": "2026-07-25T09:07:00Z",
  "based_on_storyboard_version": "1.0.0"
}
```

## 4. Renderer

```json
{
  "render_job": {
    "job_id": "render-job-8f2c1a",
    "topic_id": "java-arrays-basics",
    "status": "completed",
    "progress_percent": 100,
    "submitted_at": "2026-07-25T09:10:00Z",
    "started_at": "2026-07-25T09:10:05Z",
    "completed_at": "2026-07-25T09:12:40Z",
    "error_message": null,
    "failed_at_scene_id": null,
    "based_on_storyboard_version": "1.0.0",
    "based_on_voice_version": "1.0.0",
    "based_on_subtitle_version": "1.0.0",
    "based_on_asset_manifest_version": "1.0.0"
  },
  "video_package": {
    "package_id": "pkg-java-arrays-basics-20260725",
    "based_on_render_job_id": "render-job-8f2c1a",
    "topic_id": "java-arrays-basics",
    "topic_title": "Arrays Basics",
    "video_file_uri": "gs://forgebrain-media/java-arrays-basics/final/reel.mp4",
    "thumbnail_frame_uri": "gs://forgebrain-media/java-arrays-basics/final/thumbnail.jpg",
    "duration_seconds": 29.5,
    "resolution": "1080x1920",
    "aspect_ratio": "9:16",
    "video_codec": "h264",
    "audio_codec": "aac",
    "file_size_bytes": 18874368,
    "checksum": "sha256:4f2a9c8e1b7d6053a1f0e9c8b7a6d5e4f3c2b1a0908172635445362718293a0",
    "generated_at": "2026-07-25T09:12:40Z"
  }
}
```

The thumbnail is drawn from `scene-4-crash-resolved` — the storyboard's `emphasis_points[0]` for this topic (per `render-spec.md` Section 5) — not from the opening frame.
