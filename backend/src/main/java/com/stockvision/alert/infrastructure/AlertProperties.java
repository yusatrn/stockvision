package com.stockvision.alert.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for scheduled alert rule evaluation and staleness/cooldown rules.
 */
@ConfigurationProperties(prefix = "app.alerts")
public class AlertProperties {

    private Scheduler scheduler = new Scheduler();
    private boolean rejectStaleData = true;
    private int defaultCooldownMinutes = 60;

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler != null ? scheduler : new Scheduler();
    }

    public boolean isRejectStaleData() {
        return rejectStaleData;
    }

    public void setRejectStaleData(boolean rejectStaleData) {
        this.rejectStaleData = rejectStaleData;
    }

    public int getDefaultCooldownMinutes() {
        return defaultCooldownMinutes;
    }

    public void setDefaultCooldownMinutes(int defaultCooldownMinutes) {
        this.defaultCooldownMinutes = Math.max(1, defaultCooldownMinutes);
    }

    public static class Scheduler {
        private boolean enabled = true;
        private long fixedDelayMs = 60_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = Math.max(1000L, fixedDelayMs);
        }
    }
}
