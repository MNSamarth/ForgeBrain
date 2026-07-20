package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.models.Script;
import org.springframework.stereotype.Component;

/**
 * Dry-run YouTube Shorts adapter — see {@link AbstractDryRunPlatformPublishAdapter}. Swapping
 * this for a real adapter (calling the YouTube Data API) later means changing this class's
 * {@code publish} method only; {@link PlatformPublishAdapter} and every caller stay the same.
 */
@Component
public class YouTubeShortsPublishAdapter extends AbstractDryRunPlatformPublishAdapter {

    public YouTubeShortsPublishAdapter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public Script.Platform supportedPlatform() {
        return Script.Platform.YOUTUBE_SHORTS;
    }
}
