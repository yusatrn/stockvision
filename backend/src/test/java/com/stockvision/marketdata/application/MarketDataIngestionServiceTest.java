package com.stockvision.marketdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.market.domain.ExchangeRepository;
import com.stockvision.marketdata.api.SyncResultResponse;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.IngestionCounts;
import com.stockvision.marketdata.domain.MarketDataBarRepository;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.marketdata.domain.ProviderSyncRun;
import com.stockvision.marketdata.domain.SyncRunStatus;
import com.stockvision.marketdata.infrastructure.MarketDataProperties;
import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentRepository;
import com.stockvision.stock.domain.InstrumentType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketDataIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final ProviderMetadata METADATA =
            new ProviderMetadata(
                    "MOCK_SIMULATED",
                    "Mock",
                    DataProviderMode.SIMULATED,
                    true,
                    0,
                    "Simulated",
                    "Europe/Istanbul");

    private InstrumentRepository instrumentRepository;
    private MarketDataProvider marketDataProvider;
    private MarketDataBarRepository barRepository;
    private SyncRunAuditService syncRunAuditService;
    private MarketDataIngestionService service;

    @BeforeEach
    void setUp() {
        instrumentRepository = mock(InstrumentRepository.class);
        marketDataProvider = mock(MarketDataProvider.class);
        barRepository = mock(MarketDataBarRepository.class);
        syncRunAuditService = mock(SyncRunAuditService.class);

        MarketDataProperties properties = new MarketDataProperties();
        properties.setProvider("mock");
        properties.setDefaultLookbackDays(30);
        properties.setStaleThresholdHours(24);

        when(marketDataProvider.getProviderMetadata()).thenReturn(METADATA);
        when(syncRunAuditService.record(any(ProviderSyncRun.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Instrument thyao =
                new Instrument(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "BIST",
                        "THYAO",
                        "Turk Hava Yollari",
                        "Transportation",
                        "TRY",
                        InstrumentType.EQUITY,
                        true,
                        NOW,
                        NOW);
        when(instrumentRepository.findBySymbol("THYAO")).thenReturn(Optional.of(thyao));

        service =
                new MarketDataIngestionService(
                        mock(ExchangeRepository.class),
                        instrumentRepository,
                        marketDataProvider,
                        barRepository,
                        syncRunAuditService,
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ingestsValidBarsIdempotentlyAndRecordsSuccessAudit() {
        Instant from = NOW.minusSeconds(7L * 24 * 3600);
        OhlcvBar bar =
                new OhlcvBar(
                        NOW.minusSeconds(3600),
                        new BigDecimal("250.00"),
                        new BigDecimal("255.00"),
                        new BigDecimal("249.00"),
                        new BigDecimal("253.00"),
                        1000L,
                        "TRY",
                        BarInterval.ONE_DAY,
                        "MOCK_SIMULATED",
                        false,
                        true,
                        "Europe/Istanbul",
                        NOW);
        when(marketDataProvider.getHistoricalBars("THYAO", BarInterval.ONE_DAY, from, NOW, false))
                .thenReturn(List.of(bar));
        when(barRepository.saveAllIdempotent(any(), any()))
                .thenReturn(new IngestionCounts(0, 1));

        SyncResultResponse result =
                service.ingestBars("thyao", BarInterval.ONE_DAY, from, NOW, false);

        assertThat(result.status()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(result.barsFetched()).isEqualTo(1);
        assertThat(result.barsInserted()).isZero();
        assertThat(result.barsSkipped()).isEqualTo(1);
        assertThat(result.simulated()).isTrue();
    }

    @Test
    void rejectsMixedAdjustedFlagFromProviderAndRecordsFailedAudit() {
        Instant from = NOW.minusSeconds(7L * 24 * 3600);
        OhlcvBar mismatchedBar =
                new OhlcvBar(
                        NOW.minusSeconds(3600),
                        new BigDecimal("250.00"),
                        new BigDecimal("255.00"),
                        new BigDecimal("249.00"),
                        new BigDecimal("253.00"),
                        1000L,
                        "TRY",
                        BarInterval.ONE_DAY,
                        "MOCK_SIMULATED",
                        true,
                        true,
                        "Europe/Istanbul",
                        NOW);
        when(marketDataProvider.getHistoricalBars("THYAO", BarInterval.ONE_DAY, from, NOW, false))
                .thenReturn(List.of(mismatchedBar));

        assertThatThrownBy(
                        () -> service.ingestBars("THYAO", BarInterval.ONE_DAY, from, NOW, false))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.PROVIDER_UNAVAILABLE));

        ArgumentCaptor<ProviderSyncRun> auditCaptor =
                ArgumentCaptor.forClass(ProviderSyncRun.class);
        verify(syncRunAuditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo(SyncRunStatus.FAILED);
    }

    @Test
    void recordsFailedAuditAndThrowsProviderUnavailableWhenProviderFails() {
        Instant from = NOW.minusSeconds(7L * 24 * 3600);
        when(marketDataProvider.getHistoricalBars("THYAO", BarInterval.ONE_DAY, from, NOW, false))
                .thenThrow(new MarketDataProviderException("Simulated upstream timeout"));

        assertThatThrownBy(
                        () -> service.ingestBars("THYAO", BarInterval.ONE_DAY, from, NOW, false))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.PROVIDER_UNAVAILABLE));

        ArgumentCaptor<ProviderSyncRun> auditCaptor =
                ArgumentCaptor.forClass(ProviderSyncRun.class);
        verify(syncRunAuditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().status()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(auditCaptor.getValue().errorMessage()).contains("timeout");
    }
}
