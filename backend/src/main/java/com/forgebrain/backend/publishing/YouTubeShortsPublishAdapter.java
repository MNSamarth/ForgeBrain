package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.models.Script;

/**
 * Dry-run YouTube Shorts adapter — see {@link AbstractDryRunPlatformPublishAdapter}. Not a {@code
 * @Component}: {@link PlatformPublishAdapterBeanConfig} constructs this explicitly alongside
 * {@link YouTubeRealPublishAdapter} so {@link PlatformPublishAdapterFactory} can choose between
 * them per {@code forgebrain.publishing.upload.youtube.enabled} — with both auto-registered as
 * beans of the same {@link PlatformPublishAdapter} type, Spring would have no unambiguous
 * candidate to autowire (mirrors {@code OutputStorageBeanConfig}'s local-vs-cloud storage
 * pattern). Used directly (not through the factory) whenever real upload is disabled, which is
 * every committed profile today.
 */
public class YouTubeShortsPublishAdapter extends AbstractDryRunPlatformPublishAdapter {

    public YouTubeShortsPublishAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public Script.Platform supportedPlatform() {
        return Script.Platform.YOUTUBE_SHORTS;
    }
}
