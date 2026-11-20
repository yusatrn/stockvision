# Explainable Technical Scoring & Screener Methodology (`score-engine-v1.0.0`)

Bu doküman, StockVision AI Faz 5 teknik skorlama motorunun ağırlık politikasını, bileşen normalizasyon kurallarını, eksik/eski veri davranışını ve bilinen sınırlamalarını özetler.

---

## 1. Ağırlık Yapısı ve Konfigürasyon (`ScoreWeights`)

Toplam ağırlık kesin olarak **`100.00%`** olmalıdır (`ScoreWeights.ofExact` başlangıçta doğrular; `ScoreWeights.normalize` ise keyfi pozitif ağırlıkları `100.00%`'ye orantılar).

| Bileşen | Varsayılan Ağırlık | Minimum Gereksinim |
|---|---|---|
| `TREND` (SMA 20, EMA 20, 5 bar eğim) | %20.00 | >= 25 bar + hazır SMA/EMA |
| `RSI` (14 periyot Wilder RMA) | %15.00 | >= 15 bar + hazır RSI |
| `MACD` (12, 26, 9) | %15.00 | >= 35 bar + ardışık 2 histogram |
| `VOLUME` (20 bar hacim oranı & fiyat yönü) | %15.00 | >= 20 bar + ortalama hacim > 0 |
| `BOLLINGER` (20 bar, 2.0σ, %B) | %10.00 | >= 20 bar + hazır Bollinger |
| `MOMENTUM` (10 bar ROC + Stochastic %K/%D) | %10.00 | >= 16 bar + hazır Stochastic |
| `SUPPORT_RESISTANCE` (20 bar alt/üst kanal konumu) | %15.00 | >= 20 bar |

---

## 2. Normalizasyon ve Bileşen Katkısı

- Her bileşen **[-100.00, +100.00]** aralığında normalize edilmiş bir skor (`normalizedScore`) üretir.
- Bileşenin nihai 0–100 skora ağırlıklı katkısı `(normalizedScore * weightPercent) / 200` olarak hesaplanır (nötr merkez `50.00`).
- Nihai skor:
  $$\text{finalScore} = \text{clamp}\left(\frac{\frac{\sum c_i w_i}{100} + 100}{2},\; 0.00,\; 100.00\right)$$
- Tüm ara ve nihai hesaplamalar `BigDecimal` + `RoundingMode.HALF_UP` ile yapılır; `double` kullanılmaz.

---

## 3. Eksik ve Eski Veri (`INSUFFICIENT_DATA`) Politikası

- **Sessiz Nötrleme Yoktur:** Herhangi bir bileşenin bar sayısı yetersizse, sıfır hacim nedeniyle hacim oranı tanımsızsa veya `reject-stale-data=true` iken en son bar `asOf` zamanından `max-data-age-hours` (varsayılan `120` saat) daha eskiyse:
  - Genel durum **`INSUFFICIENT_DATA`**, `finalScore = null` ve rejim **`INSUFFICIENT_DATA`** olarak döner.
  - Eksikliğe yol açan tüm nedenler `insufficientDataReasons` listesinde açıkça belirtilir.

---

## 4. Açıklama ve Yatırım Tavsiyesi Olmama İlkesi

- Skor rejimleri (`STRONG_BULLISH_TECHNICAL`, `MODERATE_BULLISH_TECHNICAL`, `NEUTRAL_TECHNICAL`, `MODERATE_BEARISH_TECHNICAL`, `STRONG_BEARISH_TECHNICAL`) yalnızca mevcut teknik göstergelerin tanımlayıcı özetidir.
- Hiçbir yerde al/sat garantisi, kesin getiri tahmini veya yatırım danışmanlığı ifadesi kullanılmaz.
- Teknik göstergeler geçmiş (ve Faz 3–5'te simüle) veriye dayanır; gelecekteki piyasa getirilerini güvenilir biçimde öngörmez.
