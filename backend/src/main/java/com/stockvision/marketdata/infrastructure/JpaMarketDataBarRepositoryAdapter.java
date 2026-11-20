package com.stockvision.marketdata.infrastructure;

import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.IngestionCounts;
import com.stockvision.marketdata.domain.MarketDataBarRepository;
import com.stockvision.marketdata.domain.OhlcvBar;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter for querying and idempotently ingesting OHLCV market data bars.
 */
@Repository
public class JpaMarketDataBarRepositoryAdapter implements MarketDataBarRepository {

    private static final String INSERT_IDEMPOTENT_SQL =
            """
            INSERT INTO market_data_bars (
                id, instrument_id, bar_interval, bar_timestamp,
                open_price, high_price, low_price, close_price,
                volume, currency, provider, adjusted, simulated,
                source_timezone, ingested_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, bar_interval, bar_timestamp, provider, adjusted)
            DO NOTHING
            """;

    private final SpringDataMarketDataBarRepository springDataRepository;
    private final JdbcTemplate jdbcTemplate;

    public JpaMarketDataBarRepositoryAdapter(
            SpringDataMarketDataBarRepository springDataRepository, JdbcTemplate jdbcTemplate) {
        this.springDataRepository = springDataRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OhlcvBar> findBars(
            UUID instrumentId,
            BarInterval interval,
            String provider,
            boolean adjusted,
            Instant from,
            Instant to) {
        return springDataRepository
                .findAllByInstrumentIdAndBarIntervalAndProviderAndAdjustedAndBarTimestampBetweenOrderByBarTimestampAsc(
                        instrumentId, interval.code(), provider, adjusted, from, to)
                .stream()
                .map(MarketDataBarJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<OhlcvBar> findLatestBar(
            UUID instrumentId, BarInterval interval, String provider, boolean adjusted) {
        return springDataRepository
                .findFirstByInstrumentIdAndBarIntervalAndProviderAndAdjustedOrderByBarTimestampDesc(
                        instrumentId, interval.code(), provider, adjusted)
                .map(MarketDataBarJpaEntity::toDomain);
    }

    @Override
    public Set<UUID> findSyncedInstrumentIds(
            BarInterval interval, String provider, boolean adjusted, Instant from, Instant to) {
        List<UUID> ids =
                jdbcTemplate.query(
                        """
                        SELECT DISTINCT instrument_id
                        FROM market_data_bars
                        WHERE bar_interval = ?
                          AND provider = ?
                          AND adjusted = ?
                          AND bar_timestamp >= ?
                          AND bar_timestamp <= ?
                        """,
                        (rs, rowNum) -> rs.getObject(1, UUID.class),
                        interval.code(),
                        provider,
                        adjusted,
                        OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
        return Set.copyOf(ids);
    }

    @Override
    public IngestionCounts saveAllIdempotent(UUID instrumentId, List<OhlcvBar> bars) {
        if (bars == null || bars.isEmpty()) {
            return new IngestionCounts(0, 0);
        }

        int[] results =
                jdbcTemplate.batchUpdate(
                        INSERT_IDEMPOTENT_SQL,
                        new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement ps, int i)
                                    throws SQLException {
                                OhlcvBar bar = bars.get(i);
                                ps.setObject(1, UUID.randomUUID());
                                ps.setObject(2, instrumentId);
                                ps.setString(3, bar.interval().code());
                                ps.setObject(
                                        4,
                                        OffsetDateTime.ofInstant(bar.timestamp(), ZoneOffset.UTC),
                                        Types.TIMESTAMP_WITH_TIMEZONE);
                                ps.setBigDecimal(5, bar.open());
                                ps.setBigDecimal(6, bar.high());
                                ps.setBigDecimal(7, bar.low());
                                ps.setBigDecimal(8, bar.close());
                                ps.setLong(9, bar.volume());
                                ps.setString(10, bar.currency());
                                ps.setString(11, bar.provider());
                                ps.setBoolean(12, bar.adjusted());
                                ps.setBoolean(13, bar.simulated());
                                ps.setString(14, bar.sourceTimezone());
                                ps.setObject(
                                        15,
                                        OffsetDateTime.ofInstant(bar.ingestedAt(), ZoneOffset.UTC),
                                        Types.TIMESTAMP_WITH_TIMEZONE);
                            }

                            @Override
                            public int getBatchSize() {
                                return bars.size();
                            }
                        });

        int inserted = 0;
        for (int result : results) {
            if (result > 0) {
                inserted += result;
            } else if (result == Statement.SUCCESS_NO_INFO) {
                inserted++;
            }
        }
        int skipped = Math.max(0, bars.size() - inserted);
        return new IngestionCounts(inserted, skipped);
    }
}
