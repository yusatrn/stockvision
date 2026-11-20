# Technical Analysis Engine Methodology (`ta-engine-v1.0.0`)

Bu doküman, StockVision AI Faz 4 teknik analiz motorunun hesaplama yöntemini, kütüphane seçimini, ısınma (warm-up) kurallarını, uç durum politikalarını ve bilinen sınırlarını özetler.

---

## 1. Mimari Karar: TA4J Yerine Saf `BigDecimal` Motor

`DeterministicTechnicalIndicatorEngine`, `TechnicalIndicatorService` arayüzü arkasında saf Java 21 `BigDecimal` aritmetiği ile geliştirilmiştir; TA4J bağımlılığı eklenmemiştir:

- **Finansal Yuvarlama Uyumu:** Tüm ara hesaplamalarda `MathContext(16, RoundingMode.HALF_UP)`, nihai indikatör çıktılarında ise `FinancialMath.INDICATOR_SCALE` (`6` ondalık basamak, `RoundingMode.HALF_UP`) uygulanır; `double`/`float` kullanılmaz.
- **Açık Warm-up ve Eksik Veri Semantiği:** Yeterli geçmiş oluşmadan önce değerler `null` (`warmUp = true`) olarak bırakılır; kısaltılmış pencere ortalamalarıyla sessiz değer üretilmez.
- **Sıfır Look-ahead Bias:** `i` indeksindeki her nokta yalnızca `bars[0..i]` alt serisini kullanır; gelecek barlara asla erişilmez.
- **Domain Bağımsızlığı:** `com.stockvision.technicalanalysis.domain` paketi hiçbir framework veya üçüncü parti kütüphaneye bağımlı değildir.

---

## 2. Girdi Doğrulama Kuralları (`BarSeriesValidator`)

Hesaplama başlamadan önce bar serisinin aşağıdaki koşulları sağlaması zorunludur:
- Bar listesi ve elemanları `null` olamaz.
- Zaman damgaları kesinlikle artan (`bar[i].timestamp > bar[i-1].timestamp`) ve tekrarsızdır.
- Tüm barlar aynı `(interval, provider, adjusted)` bağlamına ait olmalıdır (düzeltilmiş ve düzeltilmemiş fiyatlar karıştırılmaz).

---

## 3. İndikatör Tanımları, Periyotlar ve Warm-up Kuralları

| İndikatör | Varsayılan Parametre | Yöntem & Yumuşatma | Warm-up Barı | Minimum Bar (`requiredBars`) | Çıktı Alanları |
|---|---|---|---|---|---|
| **SMA** | `period = 20` | Son `N` kapanışın basit ortalaması | `N - 1` | `N` | `value` |
| **EMA** | `period = 20` | `N-1` indeksinde `SMA(N)` seed, sonrasında `k = 2/(N+1)` | `N - 1` | `N` | `value` |
| **RSI** | `period = 14` | **Wilder RMA** (`N` indeksinde ilk `N` değişimin basit ortalaması seed, ardından `((N-1)*prev + current)/N`) | `N` | `N + 1` | `value` |
| **MACD** | `fast = 12`, `slow = 26`, `signal = 9` | `MACD = EMA(fast) - EMA(slow)`; `Signal = EMA(MACD, signal)` (`slow + signal - 2` indeksinde seed); `Histogram = MACD - Signal` | `slow + signal - 2` (`33`) | `slow + signal - 1` (`34`) | `macd`, `signal`, `histogram` |
| **Bollinger Bands** | `period = 20`, `stdDevMultiplier = 2.0` | `middle = SMA(N)`, **popülasyon** standart sapması (`variance / N` üzerinden `BigDecimal.sqrt`), `upper/lower = middle ± m*σ`, `bandwidth = (upper - lower)/middle` | `N - 1` | `N` | `upper`, `middle`, `lower`, `bandwidth` |
| **Stochastic** | `kPeriod = 14`, `dPeriod = 3` | Fast `%K = ((C - L_min) / (H_max - L_min)) * 100`; `%D = SMA(%K, dPeriod)` | `kPeriod + dPeriod - 2` (`15`) | `kPeriod + dPeriod - 1` (`16`) | `k`, `d` |
| **ATR** | `period = 14` | True Range `max(H-L, |H-C_prev|, |L-C_prev|)` üzerinde **Wilder RMA** (`N` indeksinde `TR[1..N]` ortalaması seed) | `N` | `N + 1` | `value` |
| **Rolling Volume Avg** | `period = 20` | Hacimlerin son `N` bar basit ortalaması ve `ratio = volume / average` | `N - 1` | `N` | `average`, `ratio` |

---

## 4. Uç Durum Politikaları

- **Yetersiz Tarihsel Veri:** `availableBars < requiredBars` ise seri durumu `INSUFFICIENT_DATA`, `latestValues` boş harita (`{}`) ve tüm noktalar `warmUp = true` döner. İstenen indikatörlerin bir kısmı hazırsa çalıştırma durumu `PARTIAL`, hiçbiri hazır değilse `INSUFFICIENT_DATA` olur.
- **Yatay Fiyat (Flat Price):**
  - `RSI`: `avgGain == 0` ve `avgLoss == 0` ise `50.000000` (nötr) döner; sıfıra bölme olmaz.
  - `Stochastic`: `highestHigh == lowestLow` ise `%K = 50.000000` döner.
  - `Bollinger`: `variance <= 0` ise `stdDev = 0`, `upper = middle = lower = close`, `bandwidth = 0.000000` döner.
  - `ATR`: `0.000000` döner.
- **Sıfır Hacim (Zero Volume):** `VOLUME_AVG` için `average = 0.000000` hesaplanır, ancak `average == 0` iken `ratio = null` bırakılır (sıfıra bölme hatası oluşmaz).

---

## 5. Versiyonlama ve Sınırlar

- Her API çağrısında `analysis_runs` tablosuna `engine_version` (`ta-engine-v1.0.0`), `parameters_json`, `status`, `bar_count`, tarih aralığı ve hesaplama zamanı (UTC) kaydedilir.
- Teknik göstergeler yalnızca geçmiş (veya Faz 3'te simüle) fiyat ve hacim serilerinin tanımlayıcı matematiksel özetleridir; gelecekteki getirileri güvenilir biçimde öngörmez ve yatırım tavsiyesi değildir.
