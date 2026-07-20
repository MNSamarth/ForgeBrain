package com.forgebrain.backend.publishing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.PlatformUploadConfig;
import com.forgebrain.backend.models.Script;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the single {@link PlatformPublishAdapterFactory} bean every platform's dry-run and (when
 * configured) real adapter go through. {@link YouTubeShortsPublishAdapter}, {@link
 * InstagramReelsPublishAdapter}, {@link YouTubeRealPublishAdapter}, and {@link
 * InstagramRealPublishAdapter} are all plain classes, not {@code @Component}s, precisely so this
 * is the only place that decides between them — mirrors {@code OutputStorageBeanConfig}'s
 * local-vs-cloud storage wiring exactly.
 */
@Configuration
public class PlatformPublishAdapterBeanConfig {

    @Bean
    public PlatformPublishAdapterFactory platformPublishAdapterFactory(PlatformUploadConfig config,
            ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder.build();

        Map<Script.Platform, PlatformPublishAdapter> dryRunAdapters = Map.of(
                Script.Platform.YOUTUBE_SHORTS, new YouTubeShortsPublishAdapter(objectMapper),
                Script.Platform.INSTAGRAM_REELS, new InstagramReelsPublishAdapter(objectMapper));

        Map<Script.Platform, PlatformPublishAdapter> realAdapters = Map.of(
                Script.Platform.YOUTUBE_SHORTS,
                new YouTubeRealPublishAdapter(config.youtube(), restClient, objectMapper),
                Script.Platform.INSTAGRAM_REELS,
                new InstagramRealPublishAdapter(config.instagram(), restClient, objectMapper));

        return new PlatformPublishAdapterFactory(config, dryRunAdapters, realAdapters);
    }
}
