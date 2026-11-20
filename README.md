# StockVision AI

Borsa İstanbul öncelikli, modüler monolith mimaride geliştirilen borsa analiz ve yatırım karar destek platformu.

> **Önemli Uyarı:** StockVision AI bir yatırım karar destek ve piyasa analiz aracıdır; yatırım danışmanlığı, kâr garantisi, kesin fiyat tahmini veya otomatik aracı kurum emir iletimi sunmaz.

---

## Mevcut Durum — Faz 9 (Quality, Security and Release Hardening)

Faz 1–9 tüm geliştirme ve sertleştirme aşamaları tamamlanmıştır ([docs/release-and-operations.md](file:///f:/proje/stockvision/docs/release-and-operations.md)):

- **Kimlik Doğrulama Sertleştirmesi:** [V8__create_refresh_tokens_table.sql](file:///f:/proje/stockvision/backend/src/main/resources/db/migration/V8__create_refresh_tokens_table.sql) ile `POST /api/v1/auth/refresh` (SHA-256 hashli döner refresh token ve tekrar kullanım/replay tespitinde tüm aile iptali) ve `POST /api/v1/auth/logout`.
- **Güvenlik Başlıkları & PII Redaksiyonu:** Spring Security + `next.config.ts` güvenlik başlıkları (`nosniff`, `DENY`, `Referrer-Policy`, `Permissions-Policy`), `TraceIdFilter` destekli yapılandırılmış HTTP erişim logları (`RequestAuditLoggingFilter`) ve `LogSanitizer`.
- **Bağımlılık Yaması & Hata Sınırları:** Frontend Next.js sürümü `15.5.29` yama sürümüne güncellendi; `error.tsx` ve `not-found.tsx` eklendi.
- **Tam Türkçe Arayüz & Yerelleştirme:** Tüm web arayüzü, `tr-TR` tarih/sayı/para biçimlendirmesi, backend karar destek uyarıları, deterministik teknik skor açıklamaları ve ücretsiz AI şablon açıklamaları (`ai-explain-v1.1.0-tr`) Türkçe olarak sunulur.
- **Adaylar ve Teknik Seviyeler (`/opportunities` & Hisse Detay Kartı):** Kural tabanlı `DeterministicTechnicalSetupEngine` (`setup-engine-v1.0.0`) ile momentum/kırılım adayları, destek/izleme bölgesi, kırılım eşiği, direnç/kâr realizasyonu bölgesi (R1–R2), ATR tabanlı risk sınırı (stop referansı), Risk/Ödül oranları ve BIST ±%10 günlük teorik marj referansları sunulur (yatırım tavsiyesi veya tavan tahmini içermez).
- **Tam BIST Pay Senedi Kataloğu (`V9__seed_all_bist_instruments.sql`):** [V9__seed_all_bist_instruments.sql](file:///f:/proje/stockvision/backend/src/main/resources/db/migration/V9__seed_all_bist_instruments.sql) ile piyasada işlem gören 625 aktif BIST hisse senedi piyasa değeri sırasıyla (`catalog_rank`) kataloglanmıştır; tarayıcı ve aday uç noktaları `scope` (`BIST30`, `BIST100`, `SYNCED`, `ALL`), `query` araması ve kademeli `/api/v1/market-data/sync-batch` toplu senkronizasyonunu destekler.
- **Gerçek Gecikmeli BIST Veri Seçeneği (`MARKET_DATA_PROVIDER=yahoo`):** Varsayılan `mock` sağlayıcıya ek olarak, kullanıcı tercihiyle API anahtarsız kamusal Yahoo Finance uç noktası üzerinden ~15 dakika gecikmeli (`DELAYED`, `simulated=false`) BIST verisi çeken [YahooFinanceMarketDataProvider.java](file:///f:/proje/stockvision/backend/src/main/java/com/stockvision/marketdata/infrastructure/YahooFinanceMarketDataProvider.java) adaptörü desteklenir (kişisel/yerel kullanım içindir; ticari dağıtım lisansı içermez).
- **Konteyner & Operasyon:** [backend/Dockerfile](file:///f:/proje/stockvision/backend/Dockerfile), [frontend/Dockerfile](file:///f:/proje/stockvision/frontend/Dockerfile) ve tam yığın [docker-compose.yml](file:///f:/proje/stockvision/docker-compose.yml) (`--profile full`) sağlık kontrolleriyle doğrulandı.

> **Üretim Hazırlığı Beyanı:** Sistem otomasyon testleri, güvenlik başlıkları, Türkçe arayüz ve yerel/kişisel kullanım için `mock` veya gecikmeli `yahoo` veri sağlayıcısı ile doğrulanmıştır; **ticari/canlı BIST üretim kullanımı için lisanslı veri sağlayıcısı, TLS reverse proxy ve dağıtık Redis katmanı ön koşuldur.**

---

## Teknoloji Seçimi

- **Backend:** Java 21, Spring Boot 3.4.5, Maven, Spring Web, Validation, Spring Data JPA, Spring Security, JJWT `0.12.6`, Actuator
- **Veritabanı & Migrasyon:** PostgreSQL 16, Flyway
- **API Dokümantasyonu:** `springdoc-openapi` (`2.8.8`)
- **Cache:** Redis (ihtiyaç doğduğunda sonraki fazlarda eklenecek)
- **Frontend (Faz 6–9):** Next.js `15.5.29`, React 19, TypeScript, Tailwind CSS `3.4`, Lucide Icons, TradingView Lightweight Charts `5.x` (Apache 2.0), Vitest + Testing Library
- **AI Politikası (Faz 8):** Yalnızca **ücretsiz AI** seçenekleri (yerel açık kaynak LLM veya kullanıcının sağlayacağı ücretsiz katman API anahtarı) arayüz arkasında kullanılacak ve her zaman deterministik şablon tabanlı yedek (fallback) bulunacaktır. Ücretli AI servisi eklenmeyecektir.
- **Testler:** Backend: JUnit 5, Mockito, Spring MockMvc, Testcontainers (`1.21.4`) · Frontend: Vitest + React Testing Library

---

## Ön Koşullar

- Java 21 JDK (`java -version`)
- Apache Maven 3.9+ (`mvn -version`)
- Docker Desktop / Docker Engine (PostgreSQL container ve Testcontainers testleri için)
- Node.js 20+ ve npm (`node -v`, `npm -v` — Frontend için)

---

## Kurulum ve Çalıştırma

### 1. Ortam Değişkenleri
Kök dizindeki `.env.example` dosyasını `.env` olarak kopyalayın ve en az 32 bayt uzunluğunda yerel bir `JWT_SECRET` belirleyin (gerçek secret/parolaları sürüm kontrolüne eklemeyin):

```powershell
Copy-Item .env.example .env
# PowerShell ile 32 baytlık rastgele Base64 JWT_SECRET üretme örneği:
$bytes = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
```

### 2. PostgreSQL'i Başlatma (Docker Compose)
```powershell
docker compose config --quiet
docker compose up -d postgres
docker compose ps
```

### 3. Testleri Çalıştırma (Testcontainers + Birim/Slice Testler)
Docker çalışır durumdayken aşağıdaki komut tüm birim, `@WebMvcTest` ve Testcontainers PostgreSQL entegrasyon testlerini çalıştırır (testler çalışma anında rastgele bir `JWT_SECRET` üretir):

```powershell
mvn -f backend/pom.xml test
```

### 4. Paketleme
```powershell
mvn -f backend/pom.xml clean package
```

### 5. Backend ve Frontend'i Yerelde Başlatma
Backend `local` profili `JWT_SECRET` ortam değişkenini zorunlu kılar. PowerShell'de `-D` parametrelerini tırnak içinde verin:

```powershell
$env:JWT_SECRET = "<en-az-32-bayt-yerel-anahtar>"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

Frontend için `frontend/.env.example` dosyasını `frontend/.env.local` olarak kopyalayıp çalıştırın (ayrıntılar için [frontend/README.md](file:///f:/proje/stockvision/frontend/README.md)):

```powershell
Copy-Item frontend/.env.example frontend/.env.local
npm --prefix frontend install
npm --prefix frontend run dev
```

Frontend kalite kontrolleri ve testler:

```powershell
npm --prefix frontend run lint
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
```

---

## Mevcut API Uç Noktaları

| Uç Nokta | Yetki | Açıklama |
|---|---|---|
| `GET /api/v1/health` | Açık | API durumu, UTC zaman damgası, veri sağlayıcı modu (`SIMULATED`) ve karar destek uyarısı |
| `POST /api/v1/auth/register` | Açık (Rate-limited) | Yeni kullanıcı kaydı (`202 Accepted`, anti-enumeration nötr yanıt) |
| `POST /api/v1/auth/login` | Açık (Rate-limited) | E-posta/parola doğrulama, Bearer JWT ve SHA-256 döner refresh token üretimi |
| `POST /api/v1/auth/refresh` | Açık (Rate-limited) | Döner refresh token değişimi ve replay ihlalinde aile iptali |
| `POST /api/v1/auth/logout` | Açık (Rate-limited) | Aktif refresh token'ı idempotent olarak iptal etme (`204`) |
| `GET /api/v1/users/me` | Bearer JWT | Oturum açmış aktif kullanıcının profili |
| `GET /api/v1/markets` | Bearer JWT | Aktif borsalar, para birimi, saat dilimi ve yerel seans saatleri |
| `GET /api/v1/markets/summary` | Bearer JWT | Borsa özeti, aktif enstrüman sayısı, simüle hareketliler ve tazelik bilgisi |
| `GET /api/v1/instruments` | Bearer JWT | Sembol/isim araması, borsa filtresi ve sayfalama (`page >= 0`, `1 <= size <= 100`) |
| `GET /api/v1/instruments/{symbol}` | Bearer JWT | Enstrüman detayı, son simüle fiyat, veri kaynağı ve tazelik meta verisi |
| `GET /api/v1/instruments/{symbol}/bars` | Bearer JWT | UTC tarihsel OHLCV barları (`1d`: maks 3650 gün, `1h`: maks 180 gün, `15m`: maks 30 gün; `adjusted` ayrı) |
| `GET /api/v1/instruments/{symbol}/indicators` | Bearer JWT | Deterministik teknik indikatörler (`SMA`, `EMA`, `RSI`, `MACD`, `BOLLINGER`, `STOCHASTIC`, `ATR`, `VOLUME_AVG`) + warm-up / `INSUFFICIENT_DATA` |
| `GET /api/v1/instruments/{symbol}/analysis` | Bearer JWT | Açıklanabilir 0–100 teknik skor, bileşen katkıları, UI açıklama DTO'su ve `INSUFFICIENT_DATA` kontrolü |
| `GET /api/v1/instruments/{symbol}/setups` | Bearer JWT | Tek hisse için destek/izleme bölgesi, kırılım eşiği, direnç R1–R2, risk sınırı, R/Ö ve BIST ±%10 marj referansı |
| `GET /api/v1/opportunities` | Bearer JWT | Kapsam (`BIST30`, `BIST100`, `SYNCED`, `ALL`), `query`, kategori ve sıralama ile kural tabanlı adaylar ve teknik seviyeler |
| `GET /api/v1/screener` | Bearer JWT | Kapsam (`BIST30`, `BIST100`, `SYNCED`, `ALL`), `query`, skor (`minScore`/`maxScore`), RSI (`rsiMin`/`rsiMax`), trend filtresi, sıralama ve sayfalama |
| `GET /api/v1/watchlists` | Bearer JWT | Kullanıcının tüm izleme listeleri, kalem fiyatları, veri kaynağı ve tazelik meta verisi |
| `POST /api/v1/watchlists` | Bearer JWT | Yeni adlandırılmış izleme listesi oluşturma (`LOWER(name)` benzersiz) |
| `PATCH /api/v1/watchlists/{id}` | Bearer JWT | Sahip olunan izleme listesini güncelleme |
| `DELETE /api/v1/watchlists/{id}` | Bearer JWT | Sahip olunan izleme listesini ve öğelerini silme |
| `POST /api/v1/watchlists/{id}/items` | Bearer JWT | İzleme listesine sembol ekleme (liste içi tekrara `409 CONFLICT`) |
| `DELETE /api/v1/watchlists/{id}/items/{itemId}` | Bearer JWT | İzleme listesinden öğe çıkarma |
| `GET /api/v1/portfolios` | Bearer JWT | Kullanıcının sanal portföyleri ve türetilmiş işlem/pozisyon sayıları |
| `POST /api/v1/portfolios` | Bearer JWT | Sanal portföy oluşturma (`EXCLUDED_MVP` veya `EXPLICIT_PER_TXN` ücret politikası) |
| `DELETE /api/v1/portfolios/{id}` | Bearer JWT | Sahip olunan sanal portföyü ve defterini silme |
| `GET /api/v1/portfolios/{id}/transactions` | Bearer JWT | Kronolojik `BUY`/`SELL` defter kayıtları |
| `POST /api/v1/portfolios/{id}/transactions` | Bearer JWT | Açığa satış kontrolüyle `BUY`/`SELL` kaydı ekleme |
| `DELETE /api/v1/portfolios/{id}/transactions/{txId}` | Bearer JWT | Bütünlüğü koruyarak defter kaydı silme (sonraki satışı bozarsa `409 CONFLICT`) |
| `GET /api/v1/portfolios/{id}/performance` | Bearer JWT | İşlemlerden türetilen açık pozisyonlar, ortalama maliyet, realized vs. unrealized P&L |
| `GET /api/v1/alerts` | Bearer JWT | Kullanıcının fiyat/indikatör alarm kuralları ve tetikleme/cooldown durumu |
| `POST /api/v1/alerts` | Bearer JWT | Fiyat veya indikatör eşik alarmı oluşturma (`cooldownMinutes` ile) |
| `PATCH /api/v1/alerts/{id}` | Bearer JWT | Durum (`ACTIVE`/`PAUSED`), eşik veya cooldown güncelleme |
| `DELETE /api/v1/alerts/{id}` | Bearer JWT | Sahip olunan alarm kuralını silme |
| `POST /api/v1/alerts/evaluate` | Bearer JWT | Kullanıcının aktif alarmlarını idempotent olarak hemen değerlendirme |
| `GET /api/v1/notifications` | Bearer JWT | Okunmamış sayısı ve veri tazelik/sağlayıcı bilgisiyle sayfalı bildirimler |
| `PATCH /api/v1/notifications/{id}/read` | Bearer JWT | Bildirimi UTC zaman damgasıyla okundu işaretleme |
| `POST /api/v1/notifications/read-all` | Bearer JWT | Tüm okunmamış bildirimleri okundu işaretleme |
| `GET /api/v1/instruments/{symbol}/ai-explanation` | Bearer JWT | Kalıcı skor koşusuna bağlı ücretsiz AI / deterministik şablon açıklaması |
| `POST /api/v1/instruments/{symbol}/ai-explanation/regenerate` | Bearer JWT | Önbelleği atlayarak yeni denetlenebilir açıklama kaydı üretme |
| `POST /api/v1/market-data/sync` | `ADMIN` (Bearer JWT) | Sağlayıcıdan tek sembol için idempotent tarihsel bar senkronizasyonu |
| `GET /api/v1/market-data/sync-status` | Bearer JWT | Aktif BIST kataloğunda veri senkronizasyon kapsamını raporlama |
| `POST /api/v1/market-data/sync-batch` | Bearer JWT | Senkronize edilmemiş sıradaki `batchSize` (1..50) hisse için kademeli bar senkronizasyonu |
| `GET /actuator/health` | Açık | Spring Actuator sağlık kontrolü (PostgreSQL bağlantısı dâhil) |
| `GET /actuator/info` | Açık | Uygulama adı ve sürüm bilgisi |
| `GET /v3/api-docs` | Profil bazlı (`local`/`test`) | OpenAPI 3 JSON sözleşmesi (`bearerAuth` güvenlik şeması dâhil) |
| `GET /swagger-ui/index.html` | Profil bazlı (`local`) | Swagger UI (`prod` profilinde varsayılan olarak kapalı) |

> Güvenlik gereği yukarıdakiler dışındaki tüm yollar kimlik doğrulama gerektirir ve yetkisiz erişimde `401 UNAUTHORIZED` `ApiErrorResponse` döner.

---

## Önemli Veri ve Sınırlamalar Notu

- **Piyasa verisi durumu:** Piyasa verisi sağlayıcısının lisansı, BIST kapsamı, gecikme süresi, sembol formatı ve kullanım şartları doğrulanmadan ticari canlı veri varmış gibi davranılmamalıdır. Varsayılan sağlayıcı deterministik `MockMarketDataProvider` (`MARKET_DATA_PROVIDER=mock`, `simulated: true` / `SIMULATED_ONLY`) olup isteğe bağlı olarak API anahtarı gerektirmeyen `YahooFinanceMarketDataProvider` (`MARKET_DATA_PROVIDER=yahoo`) etkinleştirilebilir. `yahoo` modu BIST sembollerini `.IS` sonekiyle (`THYAO.IS` vb.) sorgular, tüm çıktıları ~15 dakika gecikmeli (`DELAYED`, `simulated: false`) ve ticari veri dağıtım lisansı içermeyen kamusal akış olarak etiketler.
- **Tarih aralığı ve sayfalama limitleri:** `/api/v1/instruments` için `page >= 0` ve `1 <= size <= 100`; `/api/v1/instruments/{symbol}/bars` için `1d` en fazla `3650` gün, `1h` en fazla `180` gün, `15m` en fazla `30` gün sorgulanabilir. Hafta sonları ve resmi BIST tatilleri ileri doldurulmaz (forward-fill yapılmaz).
- **Bilinen eksikler ve Canlı Üretim Ön Koşulları (Faz 9+):**
  - Ticari olarak lisanslanmış, kesinti SLA garantili gerçek zamanlı BIST veri sağlayıcısı henüz bağlı değildir (`mock` simüle test verisi, `yahoo` ise yalnızca kişisel/yerel ~15 dk gecikmeli kamusal BIST verisi sağlar).
  - Teknik göstergeler, 0–100 teknik skorlar ve doğal dil açıklamaları geçmiş/gecikmeli veya simüle verilerin bilgilendirme amaçlı Türkçe özetleridir; hata/eksik içerebilir ve gelecekteki getiriyi öngörmez.
  - Temel analiz verileri (`fundamentals`) ve harici e-posta/push bildirim kanalları ileriki genişleme kapsamındadır.
  - Çoklu sunucu kopyası (horizontal scaling) dağıtımlarında bellek içi `InMemoryAuthRateLimiter` yerine dağıtık Redis hız sınırlaması ve TLS sonlandırmalı reverse proxy kullanılmalıdır (bkz. [docs/release-and-operations.md](file:///f:/proje/stockvision/docs/release-and-operations.md)).

---

## Sorun Giderme

- **Uygulama başlarken `JWT_SECRET` hatası:** `local` ve `prod` profillerinde `JWT_SECRET` ortam değişkeni zorunludur ve en az 32 bayt uzunluğunda olmalıdır.
- **Testcontainers Docker API sürüm hatası (`400 Bad Request`):** Docker Engine 29+ minimum API `1.44` ister; `backend/pom.xml` içinde `testcontainers.version=1.21.4` sabitlenmiştir. Docker Desktop'ın çalıştığından emin olun (`docker info`).
- **Port `5432` çakışması:** Yerelde başka bir PostgreSQL servisi çalışıyorsa `.env` içinde `POSTGRES_PORT` ve `DB_URL` portunu değiştirin.
- **PowerShell Maven `-D` hatası:** `-Dspring-boot.run.profiles=local` parametresi PowerShell'de bölünebilir; mutlaka `"-Dspring-boot.run.profiles=local"` şeklinde tırnak kullanın.

---

## Geliştirme Fazları

Detaylı talimatlar `prompts/` altındadır:
1. `01-foundation-backend.md` — Backend Foundation *(Tamamlandı)*
2. `02-auth-users.md` — Authentication and Users *(Tamamlandı)*
3. `03-marketdata.md` — Instruments and Market Data *(Tamamlandı)*
4. `04-technical-analysis.md` — Technical Analysis Engine *(Tamamlandı)*
5. `05-scoring-screener.md` — Explainable Scoring and Screener *(Tamamlandı)*
6. `06-frontend-dashboard.md` — Frontend Dashboard *(Tamamlandı)*
7. `07-portfolio-watchlist-alerts.md` — Portfolio, Watchlist and Alerts *(Tamamlandı)*
8. `08-ai-analysis.md` — AI Analysis Explanations (Ücretsiz AI + deterministik şablon fallback) *(Tamamlandı)*
9. `09-hardening-release.md` — Quality, Security and Release *(Tamamlandı)*
