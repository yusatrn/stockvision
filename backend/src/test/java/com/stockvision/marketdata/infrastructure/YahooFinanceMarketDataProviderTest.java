package com.stockvision.marketdata.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooFinanceMarketDataProviderTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-20T08:00:00Z");

    private HttpServer server;
    private AtomicReference<HandlerResponse> nextResponse;
    private AtomicInteger requestCount;
    private AtomicReference<String> lastRequestUri;

    @BeforeEach
    void setUp() throws IOException {
        nextResponse = new AtomicReference<>(new HandlerResponse(200, "{}"));
        requestCount = new AtomicInteger(0);
        lastRequestUri = new AtomicReference<>();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v8/finance/chart/",
                (HttpExchange exchange) -> {
                    requestCount.incrementAndGet();
                    lastRequestUri.set(exchange.getRequestURI().toString());
                    HandlerResponse resp = nextResponse.get();
                    byte[] payload = resp.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(resp.status(), payload.length);
                    exchange.getResponseBody().write(payload);
                    exchange.close();
                });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private YahooFinanceMarketDataProvider createProvider(long quoteCacheSeconds) {
        MarketDataProperties props = new MarketDataProperties();
        props.setProvider("yahoo");
        props.getYahoo().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getYahoo().setQuoteCacheSeconds(quoteCacheSeconds);
        props.validate();
        return new YahooFinanceMarketDataProvider(
                props,
                new ObjectMapper(),
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                HttpClient.newHttpClient());
    }

    @Test
    void validatesMarketDataPropertiesForMockAndYahoo() {
        MarketDataProperties mockProps = new MarketDataProperties();
        mockProps.validate();

        MarketDataProperties yahooProps = new MarketDataProperties();
        yahooProps.setProvider("YAHOO");
        yahooProps.validate();

        MarketDataProperties invalidProps = new MarketDataProperties();
        invalidProps.setProvider("unsupported");
        assertThatThrownBy(invalidProps::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported market data provider");
    }

    @Test
    void exposesDelayedNonSimulatedMetadata() {
        YahooFinanceMarketDataProvider provider = createProvider(60);
        ProviderMetadata metadata = provider.getProviderMetadata();

        assertThat(metadata.providerId()).isEqualTo(YahooFinanceMarketDataProvider.PROVIDER_ID);
        assertThat(metadata.mode()).isEqualTo(DataProviderMode.DELAYED);
        assertThat(metadata.simulated()).isFalse();
        assertThat(metadata.delayMinutes()).isEqualTo(15);
        assertThat(metadata.sourceTimezone()).isEqualTo("Europe/Istanbul");
        assertThat(metadata.licenseNote()).contains("ticari veri dağıtım lisansı içermez");
    }

    @Test
    void parsesHistoricalBarsSkipsNullsDeduplicatesAndEnforcesRange() {
        // timestamps:
        // 1794810600 -> 2026-11-16T06:30:00Z
        // 1794897000 -> 2026-11-17T06:30:00Z (duplicated below to test deduplication)
        // 1794983400 -> 2026-11-18T06:30:00Z (null OHLC -> skipped)
        // 1795069800 -> 2026-11-19T06:30:00Z
        String body =
                """
                {
                  "chart": {
                    "result": [
                      {
                        "meta": {
                          "currency": "TRY",
                          "symbol": "THYAO.IS",
                          "exchangeTimezoneName": "Europe/Istanbul"
                        },
                        "timestamp": [1794810600, 1794897000, 1794897000, 1794983400, 1795069800],
                        "indicators": {
                          "quote": [
                            {
                              "open": [319.75, 320.00, 324.50, null, 325.00],
                              "high": [324.50, 325.00, 329.75, null, 331.75],
                              "low": [318.50, 319.00, 320.50, null, 324.75],
                              "close": [323.50, 321.00, 322.25, null, 328.25],
                              "volume": [27128552, 100, 28123073, null, 31421578]
                            }
                          ]
                        }
                      }
                    ],
                    "error": null
                  }
                }
                """;
        nextResponse.set(new HandlerResponse(200, body));

        YahooFinanceMarketDataProvider provider = createProvider(60);
        List<OhlcvBar> bars =
                provider.getHistoricalBars(
                        "THYAO",
                        BarInterval.ONE_DAY,
                        Instant.ofEpochSecond(1794810600L),
                        Instant.ofEpochSecond(1795069800L),
                        false);

        assertThat(lastRequestUri.get()).contains("/v8/finance/chart/THYAO.IS?interval=1d");
        assertThat(bars).hasSize(3);
        assertThat(bars.get(0).close()).isEqualByComparingTo(new BigDecimal("323.5000"));
        assertThat(bars.get(1).close()).isEqualByComparingTo(new BigDecimal("322.2500"));
        assertThat(bars.get(1).volume()).isEqualTo(28123073L);
        assertThat(bars.get(2).close()).isEqualByComparingTo(new BigDecimal("328.2500"));
        assertThat(bars).allSatisfy(
                bar -> {
                    assertThat(bar.simulated()).isFalse();
                    assertThat(bar.provider())
                            .isEqualTo(YahooFinanceMarketDataProvider.PROVIDER_ID);
                    assertThat(bar.currency()).isEqualTo("TRY");
                    assertThat(bar.sourceTimezone()).isEqualTo("Europe/Istanbul");
                });
    }

    @Test
    void appliesAdjustedRatioOnHistoricalBarsAndMaintainsOhlcInvariants() {
        String body =
                """
                {
                  "chart": {
                    "result": [
                      {
                        "meta": {
                          "currency": "TRY",
                          "symbol": "GARAN.IS",
                          "exchangeTimezoneName": "Europe/Istanbul"
                        },
                        "timestamp": [1794897000, 1794983400],
                        "indicators": {
                          "quote": [
                            {
                              "open": [100.00, 110.00],
                              "high": [105.00, 115.00],
                              "low": [95.00, 108.00],
                              "close": [100.00, 112.00],
                              "volume": [1000, 2000]
                            }
                          ],
                          "adjclose": [
                            {
                              "adjclose": [90.00, 100.80]
                            }
                          ]
                        }
                      }
                    ],
                    "error": null
                  }
                }
                """;
        nextResponse.set(new HandlerResponse(200, body));

        YahooFinanceMarketDataProvider provider = createProvider(60);
        List<OhlcvBar> adjustedBars =
                provider.getHistoricalBars(
                        "garan",
                        BarInterval.ONE_HOUR,
                        Instant.ofEpochSecond(1794897000L),
                        Instant.ofEpochSecond(1794983400L),
                        true);

        assertThat(lastRequestUri.get()).contains("GARAN.IS?interval=1h");
        assertThat(adjustedBars).hasSize(2);
        assertThat(adjustedBars.get(0).adjusted()).isTrue();
        assertThat(adjustedBars.get(0).open()).isEqualByComparingTo(new BigDecimal("90.0000"));
        assertThat(adjustedBars.get(0).high()).isEqualByComparingTo(new BigDecimal("94.5000"));
        assertThat(adjustedBars.get(0).low()).isEqualByComparingTo(new BigDecimal("85.5000"));
        assertThat(adjustedBars.get(0).close()).isEqualByComparingTo(new BigDecimal("90.0000"));
        assertThat(adjustedBars.get(1).close()).isEqualByComparingTo(new BigDecimal("100.8000"));
    }

    @Test
    void fetchesLatestQuoteWithDelayAndUsesTtlCache() {
        String body =
                """
                {
                  "chart": {
                    "result": [
                      {
                        "meta": {
                          "currency": "TRY",
                          "symbol": "THYAO.IS",
                          "exchangeTimezoneName": "Europe/Istanbul",
                          "regularMarketPrice": 328.25,
                          "chartPreviousClose": 323.75,
                          "regularMarketVolume": 31421578,
                          "regularMarketTime": 1795100996
                        },
                        "timestamp": [1795069800],
                        "indicators": {
                          "quote": [
                            {
                              "open": [324.00],
                              "high": [331.75],
                              "low": [324.75],
                              "close": [328.25],
                              "volume": [31421578]
                            }
                          ]
                        }
                      }
                    ],
                    "error": null
                  }
                }
                """;
        nextResponse.set(new HandlerResponse(200, body));

        YahooFinanceMarketDataProvider provider = createProvider(60);
        LatestQuote first = provider.getLatestQuote("THYAO");
        LatestQuote second = provider.getLatestQuote("THYAO");

        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(lastRequestUri.get()).isEqualTo("/v8/finance/chart/THYAO.IS?interval=1d&range=1d");
        assertThat(first).isEqualTo(second);
        assertThat(first.symbol()).isEqualTo("THYAO");
        assertThat(first.price()).isEqualByComparingTo(new BigDecimal("328.2500"));
        assertThat(first.previousClose()).isEqualByComparingTo(new BigDecimal("323.7500"));
        assertThat(first.changeAmount()).isEqualByComparingTo(new BigDecimal("4.5000"));
        assertThat(first.changePercent()).isEqualByComparingTo(new BigDecimal("1.3900"));
        assertThat(first.volume()).isEqualTo(31421578L);
        assertThat(first.simulated()).isFalse();
        assertThat(first.delayMinutes()).isEqualTo(15);
        assertThat(first.provider()).isEqualTo(YahooFinanceMarketDataProvider.PROVIDER_ID);
    }

    @Test
    void throwsMarketDataProviderExceptionOnHttpErrorAndChartError() {
        YahooFinanceMarketDataProvider provider = createProvider(0);

        nextResponse.set(new HandlerResponse(500, "upstream failure"));
        assertThatThrownBy(() -> provider.getLatestQuote("THYAO"))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("HTTP 500");

        nextResponse.set(new HandlerResponse(429, "rate limited"));
        assertThatThrownBy(
                        () ->
                                provider.getHistoricalBars(
                                        "THYAO",
                                        BarInterval.FIFTEEN_MINUTES,
                                        FIXED_NOW.minusSeconds(3600),
                                        FIXED_NOW,
                                        false))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("HTTP 429");

        nextResponse.set(
                new HandlerResponse(
                        200,
                        """
                        {
                          "chart": {
                            "result": null,
                            "error": {
                              "code": "Not Found",
                              "description": "No data found, symbol may be delisted"
                            }
                          }
                        }
                        """));
        assertThatThrownBy(() -> provider.getLatestQuote("UNKNOWN"))
                .isInstanceOf(MarketDataProviderException.class)
                .hasMessageContaining("symbol may be delisted");
    }

    private record HandlerResponse(int status, String body) {
    }
}
