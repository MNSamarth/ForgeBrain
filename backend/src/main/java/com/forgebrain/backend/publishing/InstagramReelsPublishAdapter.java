package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.models.Script;

/**
 * Dry-run Instagram Reels adapter — see {@link AbstractDryRunPlatformPublishAdapter}. Not a
 * {@code @Component}: {@link PlatformPublishAdapterBeanConfig} constructs this explicitly
 * alongside {@link InstagramRealPublishAdapter} so {@link PlatformPublishAdapterFactory} can
 * choose between them per {@code forgebrain.publishing.upload.instagram.enabled} — see {@link
 * YouTubeShortsPublishAdapter}'s javadoc for why this can't stay a component-scanned bean once a
 * real adapter for the same platform exists. Used directly (not through the factory) whenever
 * real upload is disabled, which is every committed profile today.
 */
public class InstagramReelsPublishAdapter extends AbstractDryRunPlatformPublishAdapter {

    public InstagramReelsPublishAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public Script.Platform supportedPlatform() {
        return Script.Platform.INSTAGRAM_REELS;
    }
}
