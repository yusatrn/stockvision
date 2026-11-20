package com.stockvision.ai.domain;

import com.stockvision.scoring.domain.ScoreRegime;
import com.stockvision.scoring.domain.ScoreStatus;
import com.stockvision.scoring.domain.TrendDirection;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure deterministic template generator producing schema-valid explanations directly from persisted
 * {@link AiFactPack} telemetry whenever template mode is configured or an external provider fails.
 */
public class DeterministicTemplateExplanationGenerator {

    public StructuredAiExplanation generate(AiFactPack facts) {
        Objects.requireNonNull(facts, "facts must not be null");

        if (facts.status() == ScoreStatus.INSUFFICIENT_DATA || facts.finalScore() == null) {
            return buildInsufficientDataExplanation(facts);
        }

        String regimeLabel = localizeRegime(facts.regime());
        String trendLabel = localizeTrend(facts.trendDirection());
        String modeLabel = localizeMode(facts.providerMode().name());
        String freshnessLabel = localizeFreshness(facts.freshnessStatus().name());

        List<String> observedFacts = new ArrayList<>();
        observedFacts.add(
                truncate(
                        facts.symbol()
                                + " ("
                                + facts.exchangeCode()
                                + ", "
                                + facts.interval()
                                + ") bileşik teknik skoru "
                                + facts.finalScore().toPlainString()
                                + "/100 olup rejim \""
                                + regimeLabel
                                + "\", trend ise \""
                                + trendLabel
                                + "\" olarak ölçüldü."));

        if (facts.latestClose() != null) {
            observedFacts.add(
                    truncate(
                            "Son gözlenen kapanış fiyatı "
                                    + facts.latestClose().toPlainString()
                                    + " "
                                    + facts.currency()
                                    + (facts.latestRsi() != null
                                            ? "; RSI(14) değeri "
                                                    + formatTwoDecimals(facts.latestRsi())
                                            : "")
                                    + "."));
        }

        observedFacts.add(
                truncate(
                        "Veri kaynağı: "
                                + facts.providerDisplayName()
                                + " (Mod: "
                                + modeLabel
                                + ", simüle="
                                + (facts.simulated() ? "evet" : "hayır")
                                + ", tazelik="
                                + freshnessLabel
                                + ", hesaplama UTC="
                                + facts.calculatedAt()
                                + ")."));

        for (AiFactPack.FactComponent comp : facts.components()) {
            if (observedFacts.size() >= 6) {
                break;
            }
            if (comp.normalizedScore() != null) {
                observedFacts.add(
                        truncate(
                                localizeComponent(comp.type())
                                        + " bileşeni (%"
                                        + comp.weightPercent().toPlainString()
                                        + " ağırlık) "
                                        + comp.normalizedScore().toPlainString()
                                        + " puan kaydetti: "
                                        + comp.explanation()));
            }
        }

        List<String> interpretation = new ArrayList<>();
        if (facts.headline() != null && !facts.headline().isBlank()) {
            interpretation.add(truncate(facts.headline()));
        }
        if (facts.keyDrivers() != null) {
            for (String driver : facts.keyDrivers()) {
                if (interpretation.size() >= 5) {
                    break;
                }
                if (driver != null && !driver.isBlank()) {
                    interpretation.add(truncate(driver));
                }
            }
        }
        if (interpretation.isEmpty()) {
            interpretation.add(
                    "Gözlenen göstergeler ileriye dönük getiri taahhüdü olmaksızın "
                            + regimeLabel
                            + " yönünde bir bileşik denge sergilemektedir.");
        }

        List<String> uncertainties = buildUncertainties(facts);

        String summary =
                truncate(
                        facts.symbol()
                                + ", "
                                + facts.providerDisplayName()
                                + " üzerinden alınan "
                                + facts.interval()
                                + " barlarıyla deterministik "
                                + facts.finalScore().toPlainString()
                                + "/100 skor ("
                                + regimeLabel
                                + ") üretti.");

        String confidenceNote =
                buildConfidenceNote(
                        facts.simulated(),
                        facts.staleData(),
                        freshnessLabel,
                        true);

        return new StructuredAiExplanation(
                summary, observedFacts, interpretation, uncertainties, confidenceNote);
    }

    private StructuredAiExplanation buildInsufficientDataExplanation(AiFactPack facts) {
        String modeLabel = localizeMode(facts.providerMode().name());
        String freshnessLabel = localizeFreshness(facts.freshnessStatus().name());

        List<String> observedFacts = new ArrayList<>();
        observedFacts.add(
                truncate(
                        facts.symbol()
                                + " ("
                                + facts.exchangeCode()
                                + ", "
                                + facts.interval()
                                + ") analiz koşusu "
                                + facts.scoreRunId()
                                + " durumu "
                                + facts.status().name()
                                + " (bayatVeri="
                                + (facts.staleData() ? "evet" : "hayır")
                                + ") ile sonuçlandı."));
        observedFacts.add(
                truncate(
                        "Kaynak bilgisi: "
                                + facts.providerDisplayName()
                                + " (Mod: "
                                + modeLabel
                                + ", tazelik: "
                                + freshnessLabel
                                + ")."));

        List<String> interpretation = new ArrayList<>();
        interpretation.add(
                "Gerekli tarihsel bar derinliği veya taze piyasa verisi sağlanamadığı için bileşik skor üretimi durduruldu.");

        List<String> uncertainties = buildUncertainties(facts);

        String summary =
                truncate(
                        facts.symbol()
                                + " için doğrulanmış indikatör serileri veya taze barlar eksik olduğundan teknik açıklama INSUFFICIENT_DATA olarak durduruldu.");

        String confidenceNote =
                "Düşük analitik kapsama: gerekli bar geçmişi veya tazelik koşulları karşılanmadı (durum="
                        + facts.status().name()
                        + ").";

        return new StructuredAiExplanation(
                summary, observedFacts, interpretation, uncertainties, confidenceNote);
    }

    private static List<String> buildUncertainties(AiFactPack facts) {
        List<String> list = new ArrayList<>();
        if (facts.simulated()) {
            list.add(
                    "Altta yatan fiyat ve hacim verileri simüle sağlayıcıdan üretilmiştir; gerçek borsa işlemlerini temsil etmez.");
        }
        if (facts.staleData()) {
            list.add("Değerlendirme anında piyasa barları tazelik eşiğinin üzerinde (bayat) olarak işaretlenmiştir.");
        }
        if (facts.riskAndDataNotes() != null) {
            for (String note : facts.riskAndDataNotes()) {
                if (list.size() >= 6) {
                    break;
                }
                if (note != null && !note.isBlank()) {
                    list.add(truncate(note));
                }
            }
        }
        if (list.size() < 6) {
            list.add(
                    "Teknik göstergeler geriye dönük serileri özetler; modellenmemiş şirket temellerini, sermaye artırımı/temettü aksiyonlarını ve haberleri içermez.");
        }
        return list;
    }

    private static String buildConfidenceNote(
            boolean simulated, boolean staleData, String freshnessLabel, boolean ready) {
        if (staleData) {
            return "Güvenilirlik düşük: altta yatan fiyat barları bayat işaretli (" + freshnessLabel + ").";
        }
        if (simulated && ready) {
            return "Kalıcı simüle bar verilerine dayalı deterministik teknik özet (" + freshnessLabel + ").";
        }
        return "Kalıcı nicel analiz verilerine dayalı deterministik teknik özet (" + freshnessLabel + ").";
    }

    private static String localizeRegime(ScoreRegime regime) {
        if (regime == null) {
            return "Yetersiz Veri";
        }
        return switch (regime) {
            case STRONG_BULLISH_TECHNICAL -> "Güçlü Pozitif Teknik";
            case MODERATE_BULLISH_TECHNICAL -> "Ilımlı Pozitif Teknik";
            case NEUTRAL_TECHNICAL -> "Nötr Teknik";
            case MODERATE_BEARISH_TECHNICAL -> "Ilımlı Negatif Teknik";
            case STRONG_BEARISH_TECHNICAL -> "Güçlü Negatif Teknik";
            case INSUFFICIENT_DATA -> "Yetersiz Veri (INSUFFICIENT_DATA)";
        };
    }

    private static String localizeTrend(TrendDirection direction) {
        if (direction == null) {
            return "Bilinmiyor";
        }
        return switch (direction) {
            case BULLISH -> "Yükseliş Eğilimli";
            case BEARISH -> "Düşüş Eğilimli";
            case NEUTRAL -> "Yatay / Nötr";
            case UNKNOWN -> "Belirsiz";
        };
    }

    private static String localizeMode(String mode) {
        return switch (mode) {
            case "SIMULATED" -> "SİMÜLE";
            case "DELAYED" -> "GECİKMELİ";
            case "REALTIME" -> "GERÇEK ZAMANLI";
            default -> mode;
        };
    }

    private static String localizeFreshness(String freshness) {
        return switch (freshness) {
            case "FRESH" -> "Güncel";
            case "STALE" -> "Bayat Veri";
            case "SIMULATED_ONLY" -> "Simüle Akış";
            case "NO_DATA" -> "Veri Yok";
            default -> freshness;
        };
    }

    private static String localizeComponent(String type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case "TREND" -> "Trend";
            case "RSI" -> "RSI";
            case "MACD" -> "MACD";
            case "VOLUME" -> "Hacim";
            case "BOLLINGER" -> "Bollinger Bantları";
            case "MOMENTUM" -> "Momentum";
            case "SUPPORT_RESISTANCE" -> "Destek / Direnç";
            default -> type;
        };
    }

    private static String formatTwoDecimals(BigDecimal val) {
        return val.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 297) + "...";
    }
}
