# StockVision AI — Portfolio, Watchlist ve Alerts Metodolojisi (Faz 7)

Bu doküman StockVision AI içindeki kullanıcıya ait takip listeleri (`watchlist`), sanal portföy defteri (`portfolio`) ve eşik alarmlarının (`alert`) domain kurallarını, matematiksel hesaplama politikalarını ve sahiplik güvenliğini belgeler.

> **Karar Destek Notu:** Sanal portföy takibi, takip listeleri ve fiyat/indikatör alarmları yalnızca analiz ve karar destek amaçlıdır; aracı kurum emir iletimi, yatırım danışmanlığı veya kâr garantisi sunmaz.

---

## 1. Watchlist (`watchlist`) Kuralları

- **Çoklu Liste:** Her kullanıcı kendi hesabı altında birden fazla adlandırılmış takip listesi (`watchlists`) oluşturabilir. Liste isimleri kullanıcı bazında büyük/küçük harfe duyarsız benzersizlik indeksine (`LOWER(name)`) sahiptir.
- **Yinelenen Sembol Koruması:** Aynı takip listesi içine aynı enstrümanın tekrar eklenmesi hem uygulama katmanında hem veritabanı düzeyinde (`uq_watchlist_items_list_instrument`) engellenir (`409 CONFLICT`).
- **Tazelik ve Kaynak Şeffaflığı:** `GET /api/v1/watchlists` yanıtı aktif enstrümanlara ilişkin son fiyat özetini döndürürken veri kaynağını (`DataSourceMetadataResponse`) ve tazelik durumunu (`FreshnessMetadataResponse`) açıkça raporlar.

---

## 2. Sanal Portföy Defteri ve Hesaplama Politikası (`portfolio`)

- **Değişmez Defter Yaklaşımı (Ledger-First):** Sanal portföylerde pozisyon adedi, ortalama maliyet veya toplam kâr/zarar değerleri tablo üzerinde yinelenen sütunlarda tutulmaz. Tüm tutarlar salt `portfolio_transactions` tablosundaki kronolojik `BUY` / `SELL` kayıtlarından `PortfolioCalculator` aracılığıyla deterministik olarak türetilir.
- **Sıralama:** İşlemler `executed_at ASC, created_at ASC, id ASC` düzeninde tekrar oynatılır.
- **BigDecimal Hassasiyeti ve Yuvarlama (`FinancialMath`, `HALF_UP`):**
  - İşlem miktarı (`quantity`): `6` ondalık basamak (`QUANTITY_SCALE = 6`)
  - Birim fiyat ve ortalama maliyet (`unitPrice`, `averageCost`): `4` ondalık basamak (`PRICE_SCALE = 4`)
  - Parasal tutarlar (`costBasis`, `marketValue`, `realizedPnl`, `unrealizedPnl`, `feeAmount`, `taxAmount`): `2` ondalık basamak (`MONEY_SCALE = 2`)
  - Yüzde getiriler (`unrealizedPnlPercent`): `4` ondalık basamak (`PERCENTAGE_SCALE = 4`)
- **Hareketli Ağırlıklı Ortalama Maliyet (`Moving Weighted Average Cost`):**
  - `BUY`: `eklenenMaliyet = (miktar × birimFiyat) + ücretVeVergi(EXPLICIT_PER_TXN ise)`; `açıkAdet += miktar`; `açıkMaliyetTabanı += eklenenMaliyet`.
  - `SELL`:
    - `miktar > açıkAdet` durumunda işlem reddedilir (`400 VALIDATION_ERROR`).
    - Daha önce işlenmiş bir `BUY` kaydının silinmesi sonraki bir `SELL` işlemini açığa düşürecekse silme reddedilir (`409 CONFLICT`).
    - `ortalamaMaliyet = açıkMaliyetTabanı / açıkAdet`; `satılanMaliyet = ortalamaMaliyet × miktar`; `netGelir = (miktar × birimFiyat) - ücretVeVergi(EXPLICIT_PER_TXN ise)`.
    - `realizedPnl += netGelir - satılanMaliyet`; `açıkAdet -= miktar`; `açıkAdet == 0` olduğunda `açıkMaliyetTabanı` ve `ortalamaMaliyet` sıfırlanır, ancak `realizedPnl` korunur.
- **Ücret ve Vergi Politikası (`FeeTaxPolicy`):**
  - `EXCLUDED_MVP` (varsayılan): Komisyon/vergi hesaplamadan açıkça dışlanır; sıfırdan büyük `feeAmount`/`taxAmount` değerleri reddedilir (`400 VALIDATION_ERROR`).
  - `EXPLICIT_PER_TXN`: İşlem anında girilen `feeAmount` ve `taxAmount`, alış maliyetine eklenir ve satış gelirinden düşülür.
- **Gerçekleşmiş vs. Gerçekleşmemiş Kâr/Zarar Ayrımı:** `PortfolioPerformanceResponse` içinde `totalRealizedPnl` (kapatılan/kısmi satılan dilimlerden) ve `totalUnrealizedPnl` (mevcut açık pozisyonların güncel piyasa değeri farkı) ayrı alanlar olarak döndürülür. Güncel fiyat alınamadığında değerleme `PARTIAL_UNAVAILABLE` olarak işaretlenir; sıfır fiyat varsayımı yapılmaz.

---

## 3. Alarmlar ve Bildirimler (`alert`)

- **Desteklenen Koşul Türleri:** `PRICE_ABOVE`, `PRICE_BELOW`, `INDICATOR_ABOVE`, `INDICATOR_BELOW`.
- **İndikatör Alan Kataloğu (`AlertIndicatorFieldCatalog`):**
  - `SMA`, `EMA`, `RSI`, `ATR` → `value`
  - `MACD` → `macd`, `signal`, `histogram`
  - `BOLLINGER` → `upper`, `middle`, `lower`, `bandwidth`
  - `STOCHASTIC` → `k`, `d`
  - `VOLUME_AVG` → `average`, `ratio`
- **Durum Makinesi, Cooldown ve Tekrar Kurulma (Hysteresis):**
  - Kural başlangıçta `ARMED` durumundadır. Eşik sağlandığında `TRIGGERED` olur ve `cooldownMinutes` boyunca aynı yönde art arda gelen değerlendirmelerde `COOLDOWN_SUPPRESSED` ile yeni bildirim üretilmez.
  - Gözlenen değer koşulu sağlamayı bıraktığında (`NOT_MET`), kural tekrar `ARMED` durumuna döner.
- **Idempotency (Yinelenen Bildirim Önleme):** Bildirimler `ruleId:dataTimestampEpochMs:cooldownBucket` anahtarı ve veritabanındaki `uq_alert_notifications_idempotency` benzersiz kısıtıyla kaydedilir; eşzamanlı veya tekrarlanan çalıştırmalarda aynı pencere için ikinci bildirim oluşturulmaz (`DUPLICATE_SUPPRESSED`).
- **Tazelik Kontrolü ve Analiz Tablosu İzolasyonu:** `app.alerts.reject-stale-data=true` iken bayat veriyle tetikleme yapılmaz (`SKIPPED_STALE_DATA`). İndikatör alarmları `TechnicalIndicatorService` üzerinden bellekte hesaplanır, böylece periyodik iş `analysis_runs` geçmişini şişirmez. Her bildirimde sağlayıcı adı, simülasyon durumu ve veri tazelik statüsü saklanır.

---

## 4. Sahiplik ve Yetkilendirme (IDOR Koruması)

Tüm Watchlist, Portfolio ve Alert/Notification uç noktaları JWT kimlik doğrulaması (`@AuthenticationPrincipal AuthenticatedUser`) gerektirir ve veritabanı sorgularını daima `user_id = principal.userId()` ile filtreler. Başka bir kullanıcıya ait kaynağa erişim veya değiştirme girişimleri varlık bilgisi sızdırmamak için tutarlı biçimde `404 NOT_FOUND` (`ApiErrorResponse`) döndürür.
