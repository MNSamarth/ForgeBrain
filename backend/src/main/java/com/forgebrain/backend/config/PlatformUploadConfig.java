package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Real-upload enablement and credentials for {@code publishing.YouTubeRealPublishAdapter}/{@code
 * InstagramRealPublishAdapter}. Bound from {@code forgebrain.publishing.upload.*}. Holds only
 * *structure* and secret *references* — no real client secret, refresh token, or access token is
 * ever committed to this repository (see docs/CONFIGURATION.md Section 5); real values are
 * injected at runtime via environment variables or Google Secret Manager, exactly like every
 * other credential-shaped config in this project.
 *
 * @param dryRunOnly global override: when {@code true} (the default in every committed profile),
 *                   every platform uses its dry-run adapter regardless of {@code
 *                   youtube.enabled}/{@code instagram.enabled} — the safe default for local
 *                   development, CI, and any environment without real platform credentials
 * @param youtube    YouTube Shorts real-upload configuration
 * @param instagram  Instagram Reels real-upload configuration
 */
@ConfigurationProperties(prefix = "forgebrain.publishing.upload")
public record PlatformUploadConfig(
        boolean dryRunOnly,
        YouTube youtube,
        Instagram instagram
) {

    /**
     * @param enabled      opt-in switch for real YouTube uploads; when {@code false}, {@code
     *                     clientId}/{@code clientSecret}/{@code refreshToken} are never read and
     *                     the dry-run adapter is used instead
     * @param clientId     OAuth 2.0 client id for the Google Cloud project authorized to upload
     *                     to {@code channelId}
     * @param clientSecret OAuth 2.0 client secret for {@code clientId}
     * @param refreshToken a long-lived OAuth 2.0 refresh token, previously granted the {@code
     *                     https://www.googleapis.com/auth/youtube.upload} scope, exchanged for a
     *                     short-lived access token before every upload
     * @param channelId    the YouTube channel the token is authorized for — informational/
     *                     traceability only; the Data API uploads to whichever channel the token
     *                     itself is scoped to
     * @param privacyStatus {@code "private"}, {@code "unlisted"}, or {@code "public"} — defaults
     *                      to {@code "private"} in every committed profile so a misconfiguration
     *                      can never accidentally publish publicly
     * @param categoryId   YouTube video category id, e.g. {@code "27"} (Education)
     */
    public record YouTube(
            boolean enabled,
            String clientId,
            String clientSecret,
            String refreshToken,
            String channelId,
            String privacyStatus,
            String categoryId
    ) {
    }

    /**
     * @param enabled                  opt-in switch for real Instagram uploads; when {@code
     *                                 false}, {@code accessToken}/{@code igUserId} are never read
     *                                 and the dry-run adapter is used instead
     * @param accessToken              a long-lived Instagram/Facebook Graph API access token for
     *                                 {@code igUserId}, authorized for {@code
     *                                 instagram_content_publish}
     * @param igUserId                 the Instagram professional/business account id content is
     *                                 published to
     * @param publishPollIntervalSeconds seconds to wait between container-status polls while the
     *                                 Graph API processes an uploaded video before it can be
     *                                 published — {@code 0} in tests, a few seconds in production
     * @param publishPollMaxAttempts   how many times to poll before giving up and reporting a
     *                                 failed (not hung) outcome
     */
    public record Instagram(
            boolean enabled,
            String accessToken,
            String igUserId,
            int publishPollIntervalSeconds,
            int publishPollMaxAttempts
    ) {
    }
}
