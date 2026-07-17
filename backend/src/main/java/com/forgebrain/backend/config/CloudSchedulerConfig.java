package com.forgebrain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloud Scheduler configuration for a future recurring pipeline trigger. See
 * docs/CONFIGURATION.md Section 3. Bound from {@code forgebrain.cloud-scheduler.*} in
 * application.yml. Configures the trigger's schedule only — this project does not include
 * the scaling/deployment infrastructure that would run it (docs/ARCHITECTURE.md Section 10).
 *
 * @param jobName        Cloud Scheduler job identifier
 * @param cronExpression standard cron expression for run frequency
 * @param timezone       IANA timezone the cron expression is evaluated in
 */
@ConfigurationProperties(prefix = "forgebrain.cloud-scheduler")
public record CloudSchedulerConfig(
        String jobName,
        String cronExpression,
        String timezone
) {
}
