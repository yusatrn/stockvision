# StockVision AI — Sürüm Hazırlığı, Operasyon ve Piyasa Verisi Rehberi (Faz 9)

Bu doküman StockVision AI sisteminin güvenlik sertleştirmesini, ortam yapılandırma gereksinimlerini, veritabanı yedekleme/geri yükleme prosedürlerini, gözlemlenebilirlik kontrollerini ve canlı piyasa verisi sağlayıcısı entegrasyonu için ön koşulları belgeler.

> **Sürüm Durumu Notu (Açık Beyan):** Mevcut sürüm otomasyonlu doğrulama, **tam Türkçe arayüz (`tr-TR`) ve Türkçe backend açıklamaları**, varsayılan **simüle veri (`MockMarketDataProvider`, `simulated: true`)** veya kullanıcı tarafından seçilen **kamusal gecikmeli BIST verisi (`YahooFinanceMarketDataProvider`, `MARKET_DATA_PROVIDER=yahoo`, `DELAYED` ~15 dk, ticari lisansı yoktur)** ile yerel/kişisel karar-destek değerlendirmesi için sertleştirilmiştir. **Ticari lisanslı canlı BIST piyasa verisi sağlayıcısı bağlanana, TLS sonlandırma ve dağıtık Redis hız sınırlaması devreye alınana kadar canlı ticari üretim (production) finansal veri sistemi olarak iddia edilmez.**

---

## 1. Ortam Konfigürasyon Rehberi

Hiçbir parola, API anahtarı veya JWT gizli anahtarı kod içinde tutulamaz. Şablon olarak [`.env.example`](file:///f:/proje/stockvision/.env.example) ve [`frontend/.env.example`](file:///f:/proje/stockvision/frontend/.env.example) dosyalarını kullanın.

| Değişken | Profil | Açıklama |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` / `prod` | Üretimde daima `prod` kullanın (`springdoc` Swagger UI kapalı, `health.show-details=never`). |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `local` / `prod` | PostgreSQL 16 JDBC bağlantısı (`UTC` saat dilimi). |
| `JWT_SECRET` | `local` / `prod` | En az **32 bayt** (256 bit) rastgele HS256 anahtarı. Başlangıçta eksikse `JwtProperties.validate()` uygulamayı durdurur. |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` / `JWT_REFRESH_TOKEN_TTL_SECONDS` | Tüm profiller | Kısa ömürlü erişim JWT süresi (varsayılan `900s`) ve SHA-256 hashli döner refresh token ömrü (`604800s`). |
| `APP_CORS_ALLOWED_ORIGINS` | Tüm profiller | Açık kaynak origin listesi; wildcard (`*`) yasaktır (`CorsProperties.validate()`). |
| `MARKET_DATA_PROVIDER` | Tüm profiller | `mock` (varsayılan deterministik test verisi) veya `yahoo` (API anahtarı gerektirmeyen ~15 dk gecikmeli kamusal Yahoo Finance BIST verisi; kişisel/yerel kullanım içindir, ticari lisanslı değildir). |
| `MARKET_DATA_YAHOO_*` | Tüm profiller | `BASE_URL` (`https://query1.finance.yahoo.com`), `SYMBOL_SUFFIX` (`.IS`), `TIMEOUT_MS` (`10000`), `QUOTE_CACHE_SECONDS` (`60`). |
| `AI_PROVIDER` | Tüm profiller | Varsayılan `template`. Ücretsiz alternatifler: `ollama` (`AI_OLLAMA_*`) veya kullanıcının ücretsiz `AI_GEMINI_API_KEY` anahtarıyla `gemini-free`. |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend | Tarayıcıdan backend API'ye giden açık URL (eğik çizgi olmadan). |

---

## 2. Veritabanı Migrasyonları, Yedekleme ve Geri Yükleme

- **Şema Yönetimi:** Tüm şema değişiklikleri yalnızca Flyway (`V1`–`V9`) üzerinden yönetilir; `spring.jpa.hibernate.ddl-auto=validate` üretimde otomatik DDL değişikliğini yasaklar.
- **PostgreSQL Saat Dilimi:** `docker-compose.yml` ve JPA/Jackson tüm tarih-saat verilerini `UTC` olarak saklar; arayüz kullanıcı saat dilimine dönüştürür.
- **Yedekleme (Özel İkili Arşiv):**
  ```bash
  docker exec -t stockvision-postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/stockvision_utc.dump
  docker cp stockvision-postgres:/tmp/stockvision_utc.dump ./stockvision_utc_$(date -u +%Y%m%dT%H%M%SZ).dump
  ```
- **Geri Yükleme ve Doğrulama:**
  ```bash
  docker cp ./stockvision_utc.dump stockvision-postgres:/tmp/stockvision_utc.dump
  docker exec -t stockvision-postgres pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner /tmp/stockvision_utc.dump
  ```
  Geri yükleme sonrasında backend başlatıldığında Flyway `flyway_schema_history` doğrulamasını (`V1..V9`) otomatik yürütür; ardından `/actuator/health/readiness` kontrol edilmelidir.

---

## 3. Gözlemlenebilirlik, Log Redaksiyonu ve Güvenlik Başlıkları

- **Korelasyon Kimliği:** `TraceIdFilter` her HTTP isteğine doğrulanmış veya yeni üretilmiş `X-Trace-Id` atar, MDC ve hata yanıtlarına yansıtır.
- **Yapılandırılmış Erişim Logu & PII Redaksiyonu:** `RequestAuditLoggingFilter` istek gövdesi, query parametreleri veya `Authorization` başlıkları olmadan yalnızca `event=http_request method=... path=... status=... durationMs=...` loglar. `LogSanitizer`, hata ve istisna mesajlarında Bearer JWT, ham JWT, parola/API anahtarı kalıplarını ve e-posta adreslerini maskeler.
- **Güvenlik Başlıkları:** Hem Spring Security (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `Cache-Control: no-store`) hem `frontend/next.config.ts` başlıkları etkindir.
- **Actuator Sağlığı:** Yalnızca `/actuator/health` (`liveness` ve `readiness`) ile `/actuator/info` açıktır (`show-details=never`). `/actuator/env`, `/actuator/beans` gibi hassas uç noktalar kapalı/kimlik doğrulamalıdır.

---

## 4. SQL İndeksleme, Sayfalama ve N+1 Denetimi Özeti

- Tüm liste ve tarama sorguları sayfalama sınırlarına tabidir (`page >= 0`, `1 <= size <= 100` — `/api/v1/instruments`, `/api/v1/screener`, `/api/v1/notifications`).
- `InstrumentJpaEntity` ilişkileri `@EntityGraph(attributePaths = "exchange")` ile yüklenir (`spring.jpa.open-in-view=false`); watchlists, portfolios ve alerts koleksiyonları toplu (`IN`) ID sorgularıyla N+1 oluşturmadan getirilir.
- Tüm sık sorgulanan sütunlar indekslerle desteklenmiştir (`V1`–`V8`: `instruments`, `market_data_bars`, `analysis_runs`, `technical_score_runs`, `watchlists`, `portfolio_transactions`, `alert_rules`, `alert_notifications`, `refresh_tokens`).

---

## 5. Canlı Piyasa Verisi Sağlayıcı Ön Koşulları (BIST)

Gerçek veya gecikmeli bir `MarketDataProvider` adaptörü devreye alınmadan önce aşağıdakiler doğrulanmalıdır:
1. **Lisans & Dağıtım Hakları:** Borsa İstanbul (BIST) veri dağıtım lisansı (görüntüleme gecikmesi 15 dk mı gerçek zamanlı mı, türetilmiş analiz ve ekran gösterim hakları, zorunlu gecikme/kaynak ibareleri).
2. **Sembol Eşleme ve Kurumsal İşlemler:** Sağlayıcı sembol formatı (`THYAO` vs `THYAO.IS`), bölünme/temettü düzeltme politikası (`adjusted` ve `unadjusted` serilerin ayrı tutulması) ve seans saatleri.
3. **Kesinti & Tazelik Davranışı:** Sağlayıcı kesintisinde `503 PROVIDER_UNAVAILABLE` dönülmesi, eksik günlerde forward-fill yapılmaması, bayat veride skor ve alarm değerlendirmesinin durdurulması (`INSUFFICIENT_DATA` / `SKIPPED_STALE_DATA`).

---

## 6. Sürüm Kontrol Özeti (Release Readiness)

- **Tamamlanan Kontroller (Türkçe Yerelleştirme + Yerel/Kişisel Gerçek Gecikmeli Veri Seçeneği):** Backend `V1..V9` boş DB Flyway migrasyonları, OWASP odaklı JWT + rotasyonlu/iptal edilebilir refresh token yaşam döngüsü (`SHA-256`), IDOR sahiplik kontrolleri, güvenlik başlıkları, log redaksiyonu, Next.js `15.5.29` güvenlik yaması, hata sınırları (`error.tsx` / `not-found.tsx`), tam Türkçe arayüz/açıklama desteği, opsiyonel `MARKET_DATA_PROVIDER=yahoo` gecikmeli BIST adaptörü, çok aşamalı `Dockerfile` tanımları ve Docker Compose sağlık kontrolleri.
- **Üretim Öncesi Zorunlu Adımlar (Ticari Canlı Trafik / Gerçek Para Kararları İçin):**
  1. Ticari BIST veri dağıtım lisansına sahip canlı/gecikmeli piyasa verisi sağlayıcısının bağlanması ve doğrulanması (`yahoo` kamusal uç noktası yalnızca kişisel/yerel kullanım içindir).
  2. Reverse proxy / Load Balancer üzerinde TLS (HTTPS) sonlandırması ve güvenilir `X-Forwarded-For` yönetimi.
  3. Çoklu sunucu örneği için `AuthRateLimiter` arayüzünün arkasında dağıtık **Redis** uygulaması.
