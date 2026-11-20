package com.stockvision.marketdata.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockvision.common.domain.FinancialMath;
import com.stockvision.marketdata.domain.BarInterval;
import com.stockvision.marketdata.domain.DataProviderMode;
import com.stockvision.marketdata.domain.LatestQuote;
import com.stockvision.marketdata.domain.MarketDataProvider;
import com.stockvision.marketdata.domain.MarketDataProviderException;
import com.stockvision.marketdata.domain.OhlcvBar;
import com.stockvision.marketdata.domain.ProviderMetadata;
import com.stockvision.stock.domain.SymbolNormalizer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Public Yahoo Finance chart endpoint adapter supplying delayed real-world BIST market data
 * without requiring API credentials. Explicitly labeled {@code DELAYED} ({@code simulated = false})
 * and non-commercial.
 */
@Component
@ConditionalOnProperty(name = "app.marketdata.provider", havingValue = "yahoo")
public class YahooFinanceMarketDataProvider implements MarketDataProvider {

    public static final String PROVIDER_ID = "YAHOO_FINANCE_DELAYED";
    public static final String DISPLAY_NAME = "Yahoo Finance Gecikmeli BIST Verisi";
    public static final int DELAY_MINUTES = 15;
    public static final String SOURCE_TIMEZONE = "Europe/Istanbul";
    public static final String DEFAULT_CURRENCY = "TRY";
    public static final String LICENSE_NOTE =
            "Yahoo Finance kamusal uç noktasından ~15 dakika gecikmeli gerçek BIST verisi. "
                    + "Kişisel/yerel karar destek kullanımı içindir; ticari veri dağıtım lisansı içermez.";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final MarketDataProperties.Yahoo yahooProperties;
    private final ObjectMapper decimalObjectMapper;
    private final Clock clock;
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    @Autowired
    public YahooFinanceMarketDataProvider(
            MarketDataProperties properties, ObjectMapper objectMapper, Clock clock) {
        this(
                properties,
                objectMapper,
                clock,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getYahoo().getTimeoutMs()))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
    }

    YahooFinanceMarketDataProvider(
            MarketDataProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            HttpClient httpClient) {
        Objects.requireNonNull(properties, "properties must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.yahooProperties = properties.getYahoo();
        this.decimalObjectMapper =
                objectMapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.clock = clock;
        this.httpClient = httpClient;
    }

    @Override
    public ProviderMetadata getProviderMetadata() {
        return new ProviderMetadata(
                PROVIDER_ID,
                DISPLAY_NAME,
                DataProviderMode.DELAYED,
                false,
                DELAY_MINUTES,
                LICENSE_NOTE,
                SOURCE_TIMEZONE);
    }

    @Override
    public List<OhlcvBar> getHistoricalBars(
            String symbol, BarInterval interval, Instant from, Instant to, boolean adjusted) {
        Objects.requireNonNull(interval, "interval must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be strictly before to");
        }

        String normalizedSymbol = SymbolNormalizer.normalize(symbol);
        String yahooSymbol = toYahooSymbol(normalizedSymbol);
        String encodedSymbol = encodePathSegment(yahooSymbol);
        String baseUrl = trimTrailingSlash(yahooProperties.getBaseUrl());
        String uriString =
                baseUrl
                        + "/v8/finance/chart/"
                        + encodedSymbol
                        + "?interval="
                        + interval.code()
                        + "&period1="
                        + from.getEpochSecond()
                        + "&period2="
                        + to.getEpochSecond();

        JsonNode resultNode = fetchChartResult(uriString, yahooSymbol);
        JsonNode metaNode = resultNode.path("meta");
        String currency = readNonBlankText(metaNode, "currency", DEFAULT_CURRENCY);
        String sourceTimezone =
                readNonBlankText(metaNode, "exchangeTimezoneName", SOURCE_TIMEZONE);

        JsonNode timestamps = resultNode.path("timestamp");
        if (!timestamps.isArray() || timestamps.isEmpty()) {
            return List.of();
        }

        JsonNode quoteNode = resultNode.path("indicators").path("quote").path(0);
        if (!quoteNode.isObject()) {
            return List.of();
        }

        JsonNode opens = quoteNode.path("open");
        JsonNode highs = quoteNode.path("high");
        JsonNode lows = quoteNode.path("low");
        JsonNode closes = quoteNode.path("close");
        JsonNode volumes = quoteNode.path("volume");
        JsonNode adjCloses = resultNode.path("indicators").path("adjclose").path(0).path("adjclose");

        Instant ingestedAt = clock.instant();
        Map<Instant, OhlcvBar> dedupedBars = new TreeMap<>();

        for (int i = 0; i < timestamps.size(); i++) {
            JsonNode tsNode = timestamps.get(i);
            if (tsNode == null || !tsNode.isNumber()) {
                continue;
            }
            Instant barTimestamp = Instant.ofEpochSecond(tsNode.asLong());
            if (barTimestamp.isBefore(from) || barTimestamp.isAfter(to)) {
                continue;
            }

            BigDecimal open = readPositiveDecimal(opens.get(i));
            BigDecimal high = readPositiveDecimal(highs.get(i));
            BigDecimal low = readPositiveDecimal(lows.get(i));
            BigDecimal close = readPositiveDecimal(closes.get(i));
            if (open == null || high == null || low == null || close == null) {
                continue;
            }

            if (adjusted && adjCloses.isArray() && i < adjCloses.size()) {
                BigDecimal adjClose = readPositiveDecimal(adjCloses.get(i));
                if (adjClose != null) {
                    BigDecimal ratio = adjClose.divide(close, FinancialMath.MATH_CONTEXT);
                    open = open.multiply(ratio, FinancialMath.MATH_CONTEXT);
                    high = high.multiply(ratio, FinancialMath.MATH_CONTEXT);
                    low = low.multiply(ratio, FinancialMath.MATH_CONTEXT);
                    close = adjClose;
                }
            }

            open = FinancialMath.scalePrice(open);
            high = FinancialMath.scalePrice(high);
            low = FinancialMath.scalePrice(low);
            close = FinancialMath.scalePrice(close);

            if (open.compareTo(BigDecimal.ZERO) <= 0
                    || high.compareTo(BigDecimal.ZERO) <= 0
                    || low.compareTo(BigDecimal.ZERO) <= 0
                    || close.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Guard against minor sub-tick rounding inversions on adjusted/intraday bars.
            high = high.max(open).max(close).max(low);
            low = low.min(open).min(close).min(high);

            long volume = readNonNegativeLong(volumes.get(i));

            dedupedBars.put(
                    barTimestamp,
                    new OhlcvBar(
                            barTimestamp,
                            open,
                            high,
                            low,
                            close,
                            volume,
                            currency,
                            interval,
                            PROVIDER_ID,
                            adjusted,
                            false,
                            sourceTimezone,
                            ingestedAt));
        }

        return List.copyOf(dedupedBars.values());
    }

    @Override
    public LatestQuote getLatestQuote(String symbol) {
        String normalizedSymbol = SymbolNormalizer.normalize(symbol);
        Instant now = clock.instant();
        long cacheSeconds = yahooProperties.getQuoteCacheSeconds();

        if (cacheSeconds > 0L) {
            CachedQuote cached = quoteCache.get(normalizedSymbol);
            if (cached != null && now.isBefore(cached.expiresAt())) {
                return cached.quote();
            }
        }

        String yahooSymbol = toYahooSymbol(normalizedSymbol);
        String encodedSymbol = encodePathSegment(yahooSymbol);
        String baseUrl = trimTrailingSlash(yahooProperties.getBaseUrl());
        String uriString =
                baseUrl + "/v8/finance/chart/" + encodedSymbol + "?interval=1d&range=1d";

        JsonNode resultNode = fetchChartResult(uriString, yahooSymbol);
        JsonNode metaNode = resultNode.path("meta");

        String currency = readNonBlankText(metaNode, "currency", DEFAULT_CURRENCY);
        String sourceTimezone =
                readNonBlankText(metaNode, "exchangeTimezoneName", SOURCE_TIMEZONE);

        BigDecimal price = readPositiveDecimal(metaNode.get("regularMarketPrice"));
        if (price == null) {
            price = extractLastClose(resultNode);
        }
        if (price == null) {
            throw new MarketDataProviderException(
                    "Yahoo Finance did not return a positive quote price for " + yahooSymbol);
        }

        BigDecimal previousClose = resolvePreviousClose(metaNode, price);
        BigDecimal scaledPrice = FinancialMath.scalePrice(price);
        BigDecimal scaledPreviousClose = FinancialMath.scalePrice(previousClose);
        BigDecimal changeAmount =
                FinancialMath.scalePrice(scaledPrice.subtract(scaledPreviousClose));
        BigDecimal changePercent =
                scaledPreviousClose.compareTo(BigDecimal.ZERO) > 0
                        ? FinancialMath.divide(
                                changeAmount.multiply(ONE_HUNDRED, FinancialMath.MATH_CONTEXT),
                                scaledPreviousClose,
                                FinancialMath.PERCENTAGE_SCALE)
                        : FinancialMath.scalePercentage(BigDecimal.ZERO);

        long volume = readNonNegativeLong(metaNode.get("regularMarketVolume"));
        if (volume == 0L) {
            volume = extractLastVolume(resultNode);
        }

        long marketEpoch = readNonNegativeLong(metaNode.get("regularMarketTime"));
        Instant quotedAt = marketEpoch > 0L ? Instant.ofEpochSecond(marketEpoch) : now;

        LatestQuote quote =
                new LatestQuote(
                        normalizedSymbol,
                        scaledPrice,
                        scaledPreviousClose,
                        changeAmount,
                        changePercent,
                        volume,
                        currency,
                        quotedAt,
                        PROVIDER_ID,
                        false,
                        DELAY_MINUTES,
                        sourceTimezone);

        if (cacheSeconds > 0L) {
            quoteCache.put(
                    normalizedSymbol,
                    new CachedQuote(quote, now.plusSeconds(cacheSeconds)));
        }
        return quote;
    }

    private JsonNode fetchChartResult(String uriString, String yahooSymbol) {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(uriString))
                        .timeout(Duration.ofMillis(yahooProperties.getTimeoutMs()))
                        .header("User-Agent", yahooProperties.getUserAgent())
                        .header("Accept", "application/json")
                        .GET()
                        .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(
                    "Interrupted while querying Yahoo Finance for " + yahooSymbol, ex);
        } catch (IOException ex) {
            throw new MarketDataProviderException(
                    "Failed to query Yahoo Finance for " + yahooSymbol, ex);
        }

        if (response.statusCode() != 200) {
            throw new MarketDataProviderException(
                    "Yahoo Finance returned HTTP "
                            + response.statusCode()
                            + " for "
                            + yahooSymbol);
        }

        JsonNode root;
        try {
            root = decimalObjectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new MarketDataProviderException(
                    "Malformed JSON response from Yahoo Finance for " + yahooSymbol, ex);
        }

        JsonNode chartNode = root.path("chart");
        JsonNode errorNode = chartNode.get("error");
        if (errorNode != null && !errorNode.isNull()) {
            String desc = errorNode.path("description").asText("Unknown chart error");
            throw new MarketDataProviderException(
                    "Yahoo Finance error for " + yahooSymbol + ": " + desc);
        }

        JsonNode results = chartNode.path("result");
        if (!results.isArray() || results.isEmpty() || !results.get(0).isObject()) {
            throw new MarketDataProviderException(
                    "Yahoo Finance returned empty chart result for " + yahooSymbol);
        }
        return results.get(0);
    }

    private String toYahooSymbol(String normalizedSymbol) {
        if (normalizedSymbol.contains(".")) {
            return normalizedSymbol;
        }
        String suffix = yahooProperties.getSymbolSuffix();
        return (suffix == null || suffix.isBlank()) ? normalizedSymbol : normalizedSymbol + suffix.trim();
    }

    private static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static BigDecimal resolvePreviousClose(JsonNode metaNode, BigDecimal currentPrice) {
        BigDecimal explicitPrev = readPositiveDecimal(metaNode.get("previousClose"));
        if (explicitPrev != null) {
            return explicitPrev;
        }
        BigDecimal chartPrev = readPositiveDecimal(metaNode.get("chartPreviousClose"));
        if (chartPrev != null) {
            return chartPrev;
        }
        BigDecimal fulldayChange = readDecimal(metaNode.get("fulldayChange"));
        if (fulldayChange != null) {
            BigDecimal derived = currentPrice.subtract(fulldayChange);
            if (derived.compareTo(BigDecimal.ZERO) > 0) {
                return derived;
            }
        }
        return currentPrice;
    }

    private static BigDecimal extractLastClose(JsonNode resultNode) {
        JsonNode closes = resultNode.path("indicators").path("quote").path(0).path("close");
        if (!closes.isArray()) {
            return null;
        }
        for (int i = closes.size() - 1; i >= 0; i--) {
            BigDecimal candidate = readPositiveDecimal(closes.get(i));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static long extractLastVolume(JsonNode resultNode) {
        JsonNode volumes = resultNode.path("indicators").path("quote").path(0).path("volume");
        if (!volumes.isArray()) {
            return 0L;
        }
        for (int i = volumes.size() - 1; i >= 0; i--) {
            long vol = readNonNegativeLong(volumes.get(i));
            if (vol > 0L) {
                return vol;
            }
        }
        return 0L;
    }

    private static BigDecimal readPositiveDecimal(JsonNode node) {
        BigDecimal value = readDecimal(node);
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value;
    }

    private static BigDecimal readDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String raw = node.asText().trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static long readNonNegativeLong(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return 0L;
        }
        long val = node.asLong();
        return Math.max(0L, val);
    }

    private static String readNonBlankText(JsonNode node, String field, String fallback) {
        if (node == null || !node.isObject()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            return fallback;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? fallback : text;
    }

    private record CachedQuote(LatestQuote quote, Instant expiresAt) {
    }
}
