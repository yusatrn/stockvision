package com.stockvision.scoring.infrastructure;

import com.stockvision.scoring.domain.ScoreComponentType;
import com.stockvision.scoring.domain.ScoreWeights;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized scoring freshness rules and configurable component weights.
 */
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringProperties {

    private long maxDataAgeHours = 120L;
    private boolean rejectStaleData = true;
    private Weights weights = new Weights();

    @PostConstruct
    public void validate() {
        if (maxDataAgeHours < 1L) {
            throw new IllegalStateException("app.scoring.max-data-age-hours must be >= 1");
        }
        toScoreWeights();
    }

    public ScoreWeights toScoreWeights() {
        Map<ScoreComponentType, BigDecimal> map = new EnumMap<>(ScoreComponentType.class);
        map.put(ScoreComponentType.TREND, weights.getTrend());
        map.put(ScoreComponentType.RSI, weights.getRsi());
        map.put(ScoreComponentType.MACD, weights.getMacd());
        map.put(ScoreComponentType.VOLUME, weights.getVolume());
        map.put(ScoreComponentType.BOLLINGER, weights.getBollinger());
        map.put(ScoreComponentType.MOMENTUM, weights.getMomentum());
        map.put(ScoreComponentType.SUPPORT_RESISTANCE, weights.getSupportResistance());
        return ScoreWeights.ofExact(map);
    }

    public long getMaxDataAgeHours() {
        return maxDataAgeHours;
    }

    public void setMaxDataAgeHours(long maxDataAgeHours) {
        this.maxDataAgeHours = maxDataAgeHours;
    }

    public boolean isRejectStaleData() {
        return rejectStaleData;
    }

    public void setRejectStaleData(boolean rejectStaleData) {
        this.rejectStaleData = rejectStaleData;
    }

    public Weights getWeights() {
        return weights;
    }

    public void setWeights(Weights weights) {
        this.weights = weights != null ? weights : new Weights();
    }

    /**
     * Component weight configuration in percent (must sum to 100.00).
     */
    public static class Weights {
        private BigDecimal trend = new BigDecimal("20");
        private BigDecimal rsi = new BigDecimal("15");
        private BigDecimal macd = new BigDecimal("15");
        private BigDecimal volume = new BigDecimal("15");
        private BigDecimal bollinger = new BigDecimal("10");
        private BigDecimal momentum = new BigDecimal("10");
        private BigDecimal supportResistance = new BigDecimal("15");

        public BigDecimal getTrend() {
            return trend;
        }

        public void setTrend(BigDecimal trend) {
            this.trend = trend;
        }

        public BigDecimal getRsi() {
            return rsi;
        }

        public void setRsi(BigDecimal rsi) {
            this.rsi = rsi;
        }

        public BigDecimal getMacd() {
            return macd;
        }

        public void setMacd(BigDecimal macd) {
            this.macd = macd;
        }

        public BigDecimal getVolume() {
            return volume;
        }

        public void setVolume(BigDecimal volume) {
            this.volume = volume;
        }

        public BigDecimal getBollinger() {
            return bollinger;
        }

        public void setBollinger(BigDecimal bollinger) {
            this.bollinger = bollinger;
        }

        public BigDecimal getMomentum() {
            return momentum;
        }

        public void setMomentum(BigDecimal momentum) {
            this.momentum = momentum;
        }

        public BigDecimal getSupportResistance() {
            return supportResistance;
        }

        public void setSupportResistance(BigDecimal supportResistance) {
            this.supportResistance = supportResistance;
        }
    }
}
