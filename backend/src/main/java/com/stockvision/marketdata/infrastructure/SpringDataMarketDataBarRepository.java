package com.stockvision.marketdata.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link MarketDataBarJpaEntity}.
 */
public interface SpringDataMarketDataBarRepository
        extends JpaRepository<MarketDataBarJpaEntity, UUID> {

    List<MarketDataBarJpaEntity>
            findAllByInstrumentIdAndBarIntervalAndProviderAndAdjustedAndBarTimestampBetweenOrderByBarTimestampAsc(
                    UUID instrumentId,
                    String barInterval,
                    String provider,
                    boolean adjusted,
                    Instant from,
                    Instant to);

    Optional<MarketDataBarJpaEntity>
            findFirstByInstrumentIdAndBarIntervalAndProviderAndAdjustedOrderByBarTimestampDesc(
                    UUID instrumentId, String barInterval, String provider, boolean adjusted);
}
