# Agent Instructions

## Ürün ilkeleri
- Ürün adı: StockVision AI.
- Ürün, yatırım karar destek ve piyasa analiz aracıdır; yatırım danışmanlığı veya otomatik emir sistemi değildir.
- Hiçbir yerde kâr garantisi, kesin tahmin veya risksiz getiri iddiası kullanma.
- Analiz çıktılarında veri zamanı, veri kaynağı, gecikme durumu ve kullanılan indikatörleri görünür kıl.
- Eksik/eski veri varsa skor üretimini durdur veya sonucu açıkça `INSUFFICIENT_DATA` olarak işaretle.

## Mühendislik ilkeleri
- Önce mevcut repository'yi incele; var olan kodu sebepsiz yere silme veya yeniden yazma.
- Küçük, gözden geçirilebilir adımlarla ilerle.
- Her işte ilgili testleri ekle ve çalıştır.
- Derleme/test hatalarını çözmeden işi tamamlandı sayma.
- Secret, API key veya parola kod içine yazma. `.env.example` kullan.
- API sözleşmelerini OpenAPI ile belgele.
- Veritabanı değişikliklerini Flyway migration ile yap; Hibernate otomatik şema güncellemesini production için kullanma.
- Tarih/saatleri UTC sakla; arayüzde kullanıcı saat dilimine göre göster.
- Para ve finansal hesaplarda `BigDecimal` kullan; `double` kullanma.
- Fiyat ve indikatör hesaplarında yuvarlama politikasını açıkça tanımla.
- Hataları tutarlı bir API hata formatıyla dön.
- Loglarda token, kişisel veri veya secret yazma.

## Mimari
- İlk sürüm modüler monolith.
- Backend paketleri: `auth`, `user`, `market`, `stock`, `marketdata`, `technicalanalysis`, `fundamentals`, `scoring`, `portfolio`, `watchlist`, `alert`, `ai`, `common`.
- Controller -> application/service -> domain -> infrastructure yönünde bağımlılık kur.
- Domain katmanı framework ve dış sağlayıcılardan mümkün olduğunca bağımsız olsun.
- Dış piyasa verisi `MarketDataProvider` arayüzü arkasından gelsin.
- İlk aşamada mock sağlayıcı; gerçek sağlayıcı sadece kullanıcı tarafından seçilip erişim bilgileri sağlandığında.

## Güvenlik
- Şifreleri BCrypt/Argon2 ile hashle.
- JWT doğrulama, rol bazlı yetkilendirme, input validation ve rate limiting düşün.
- CORS'u açık wildcard bırakma.
- Kullanıcı sadece kendi portföyünü, alarmını ve watchlist'ini görebilsin.
- Hassas konfigürasyonlar environment variable üzerinden gelsin.

## Tamamlanma ölçütü
Bir görev ancak kod, test, dokümantasyon ve çalıştırma talimatları güncellendiyse tamamlanmıştır. Son raporda değişen dosyaları, test komutlarını, test sonuçlarını, bilinen eksikleri ve sonraki adımı yaz.
