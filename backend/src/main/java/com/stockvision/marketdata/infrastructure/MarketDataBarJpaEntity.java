package com.stockvision.marketdata.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.OhlcvBar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code market_data_bars} table.
 */
@Entity
@Table(name = "market_data_bars")
public class MarketDataBarJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "bar_interval", nullable = false, length = 16)
    private String barInterval;

    @Column(name = "bar_timestamp", nullable = false)
    private Instant barTimestamp;

    @Column(name = "open_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "provider", nullable = false, length = 64)
    private String provider;

    @Column(name = "adjusted", nullable = false)
    private boolean adjusted;

    @Column(name = "simulated", nullable = false)
    private boolean simulated;

    @Column(name = "source_timezone", nullable = false, length = 64)
    private String sourceTimezone;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected MarketDataBarJpaEntity() {
    }

    public OhlcvBar toDomain() {
        return new OhlcvBar(
                barTimestamp,
                openPrice,
                highPrice,
                lowPrice,
                closePrice,
                volume,
                currency,
                BarInterval.fromCode(barInterval),
                provider,
                adjusted,
                simulated,
                sourceTimezone,
                ingestedAt);
    }
}
