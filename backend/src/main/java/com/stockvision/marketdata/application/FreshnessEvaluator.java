package com.stockvision.marketdata.application;

import com.stockvision.marketdata.api.FreshnessMetadataResponse;
import com.stockvision.marketdata.domain.FreshnessStatus;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.infrastructure.MarketDataProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Evaluates data freshness and synchronization status without concealing simulated sources.
 */
@Component
public class FreshnessEvaluator {

    private final MarketDataProperties properties;
    private final Clock clock;

    public FreshnessEvaluator(MarketDataProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public FreshnessMetadataResponse evaluate(
            ProviderMetadata metadata,
            Instant latestDataTimestamp,
            Optional<ProviderSyncRun> latestSyncRun) {
        Instant now = clock.instant();
        Long ageMinutes =
                latestDataTimestamp != null
                        ? Math.max(0L, Duration.between(latestDataTimestamp, now).toMinutes())
                        : null;
        boolean stale =
                latestDataTimestamp == null
                        || Duration.between(latestDataTimestamp, now).toHours()
                                >= properties.getStaleThresholdHours();

        FreshnessStatus status;
        String note;
        if (metadata.simulated()) {
            status = FreshnessStatus.SIMULATED_ONLY;
            note = "Deterministik simüle veri etkindir; gerçek borsa piyasa verisi değildir.";
        } else if (latestDataTimestamp == null) {
            status = FreshnessStatus.NO_DATA;
            note = "İstenen ölçütler için kayıtlı piyasa verisi bulunmamaktadır.";
        } else if (stale) {
            status = FreshnessStatus.STALE;
            note = "Kayıtlı piyasa verisi yapılandırılmış tazelik eşiğini aşmıştır.";
        } else {
            status = FreshnessStatus.FRESH;
            note = "Piyasa verisi yapılandırılmış tazelik eşiği içerisindedir.";
        }

        return new FreshnessMetadataResponse(
                status,
                stale,
                metadata.simulated(),
                latestDataTimestamp,
                latestSyncRun.map(ProviderSyncRun::completedAt).orElse(null),
                latestSyncRun.map(ProviderSyncRun::status).orElse(null),
                ageMinutes,
                note);
    }
}
