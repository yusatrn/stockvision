# Authentication, Token Handling & Rate-Limit Design Note

Bu doküman Faz 2 kapsamında eklenen kimlik doğrulama (`auth`) ve kullanıcı (`user`) modüllerinin token yönetimini, yerel test kimlik bilgisi stratejisini ve hız sınırlama (rate-limit) tasarımını özetler.

---

## 1. JWT Access Token Yönetimi

- **Algoritma ve Anahtar:** HMAC-SHA256 (`HS256`, JJWT `0.12.6`). İmza anahtarı yalnızca `JWT_SECRET` (`app.security.jwt.secret`) ortam değişkeninden okunur ve başlangıçta en az 32 bayt (256 bit) uzunluğunda olması zorunlu tutulur. Hiçbir kaynak veya test dosyasında sabit gizli anahtar tutulmaz.
- **Taşınan Claim'ler:**
  - `sub`: Kullanıcı UUID'si (e-posta gibi kişisel veriler yerine değişmez UUID kullanılır)
  - `iss`: `JWT_ISSUER` (varsayılan `stockvision-api`)
  - `jti`: Benzersiz token kimliği (UUID)
  - `iat`, `exp`: UTC saniye hassasiyetli oluşturma ve son kullanma zamanları
  - `role`: Kullanıcı rolü (`USER` / `ADMIN`)
  - `token_type`: `"access"`
- **Varsayılan Süre (TTL):** `900` saniye (15 dakika, `60..86400` aralığında yapılandırılabilir).
- **Stateless Doğrulama + Hesap Durumu Kontrolü:** `JwtAuthenticationFilter` imzayı, yayıncıyı ve son kullanma zamanını doğruladıktan sonra kullanıcının veritabanında hâlâ `ACTIVE` durumda olup olmadığını denetler; `SUSPENDED` veya `LOCKED` hesapların mevcut token'ları anında reddedilir.
- **Sürümleştirme (Faz 9):** Faz 2'deki kısa ömürlü JWT erişim token'larına ek olarak Faz 9 kapsamında [V8__create_refresh_tokens_table.sql](file:///f:/proje/stockvision/backend/src/main/resources/db/migration/V8__create_refresh_tokens_table.sql) (`refresh_tokens`) tablosuyla `POST /api/v1/auth/refresh` (tek kullanımlık rotasyon + tekrar kullanım tespitinde aktif aile iptali) ve `POST /api/v1/auth/logout` (idempotent iptal) uç noktaları eklendi. Ham refresh token'lar veritabanında asla saklanmaz; `SHA-256` özetleri (`RefreshTokenHasher`) saklanır.

---

## 2. Parola Hashleme ve Enumeration Önleme

- **Hashleme:** BCrypt (`BCryptPasswordEncoder`, `BCRYPT_STRENGTH` varsayılan `12`, test profilinde hız için `10`). `passwordHash` hiçbir DTO veya `toString()` çıktısında yer almaz ve loglanmaz.
- **E-posta Normalizasyonu:** `EmailNormalizer`, Türkçe gibi yerel ayarlardan etkilenmemek için `Locale.ROOT` ile `trim().toLowerCase(Locale.ROOT)` uygular.
- **Kayıt (`POST /api/v1/auth/register`):** Kayıt var olan bir e-postayla çağrılsa bile parola önce hashlenir ve aynı `202 Accepted` nötr yanıtı (`ACCEPTED`) döner; böylece saldırganların kayıtlı e-postaları tespit etmesi engellenir.
- **Giriş (`POST /api/v1/auth/login`):** Bilinmeyen e-posta, yanlış parola veya askıya alınmış/kilitli hesap durumlarının tümünde aynı `401 UNAUTHORIZED` (`Invalid email or password`) yanıtı verilir ve bilinmeyen kullanıcıda zamanlama farkını azaltmak için sahte bir BCrypt doğrulaması çalıştırılır.

---

## 3. Yerel Test Kimlik Bilgisi Stratejisi

- Üretim veya test veritabanına sabit demo kullanıcı/parola seed edilmez.
- Yerel geliştirme için:
  1. `.env.example` temel alınarak yerel bir `.env` oluşturulur ve `JWT_SECRET` rastgele en az 32 baytlık bir değerle doldurulur.
  2. Test/geliştirme kullanıcıları `POST /api/v1/auth/register` üzerinden oluşturulup `POST /api/v1/auth/login` ile Bearer token alınır.
- Otomatik testlerde (`TestSecuritySupport`), her test JVM koşumu için `SecureRandom` ile bellekte rastgele 32 baytlık bir anahtar üretilir; test kaynak dosyalarında statik anahtar veya parola tutulmaz.

---

## 4. Rate Limiting Tasarımı ve Sınırları

- **Mevcut Uygulama:** `AuthRateLimitFilter` ve `InMemoryAuthRateLimiter`, `POST /api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/refresh` ve `/api/v1/auth/logout` uç noktalarını sabit zaman penceresi (fixed-window) sayacı ile korur (`AUTH_RATE_LIMIT_MAX_REQUESTS`, varsayılan `10`; `AUTH_RATE_LIMIT_WINDOW_SECONDS`, varsayılan `60`).
- **Bellek Koruması:** Anahtar haritası `maxTrackedKeys` (varsayılan `10.000`) ile sınırlandırılmıştır; sınır dolduğunda süresi dolmuş pencereler temizlenir.
- **Anahtarlama Notu:** İstemci kimliği yalnızca `HttpServletRequest.getRemoteAddr()` üzerinden alınır; güvenilir bir reverse proxy yapılandırması doğrulanmadan `X-Forwarded-For` başlığına körü körüne güvenilmez.
- **Sınırlar ve Ölçekleme Yolu:** Bellek içi sınırlayıcı yalnızca tek uygulama örneği için geçerlidir. Çoklu kopya (horizontal scaling) veya sıkı dağıtık koruma gerektiğinde `AuthRateLimiter` arayüzünün arkasında **Redis** tabanlı (ör. sliding-window/token-bucket) bir implementasyon devreye alınacaktır.
