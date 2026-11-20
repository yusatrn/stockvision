# Product Requirements — StockVision AI

## Vizyon
Borsa İstanbul öncelikli, sonradan başka piyasalara genişleyebilen; hisse keşfi, teknik analiz, temel veri görünümü, portföy takibi ve koşullu alarmlar sunan web tabanlı analiz platformu.

## Hedef kullanıcılar
- Piyasayı takip eden bireysel yatırımcılar
- Teknik göstergelerle hisse tarayan kullanıcılar
- Portföy performansını tek ekranda izlemek isteyen kullanıcılar

## MVP kapsamı
1. Kullanıcı kaydı, giriş ve profil.
2. Hisse listesi, sembol arama ve hisse detay sayfası.
3. Tarihsel OHLCV grafik verisi.
4. RSI, MACD, SMA, EMA, Bollinger Bands ve hacim göstergeleri.
5. Açıklanabilir 0–100 teknik skor.
6. Watchlist.
7. Sanal portföy ve işlem kaydı.
8. Fiyat ve indikatör bazlı alarm.
9. Dashboard ve temel piyasa özeti.
10. Yönetilebilir veri sağlayıcı adaptörü ve mock veri.

## MVP dışı
- Gerçek aracı kurum entegrasyonu ve otomatik emir iletimi
- Kesin fiyat tahmini
- Lisanssız gerçek zamanlı veri dağıtımı
- Sosyal yatırım / kopya işlem
- Kurumsal çok kiracılı yapı

## Temel kullanıcı akışları
- Kullanıcı kayıt olur ve giriş yapar.
- Sembol arar, hisse detayına gider.
- Zaman aralığı seçerek grafik ve göstergeleri inceler.
- Analiz skorunun bileşenlerini ve gerekçelerini görür.
- Hisseyi watchlist'e ekler.
- Sanal portföyüne alım/satım işlemi girer.
- Fiyat veya indikatör koşuluna alarm kurar.

## Analiz skoru
İlk sürümde örnek ağırlıklar:
- Trend (EMA/SMA): %20
- RSI: %15
- MACD: %15
- Hacim: %15
- Bollinger: %10
- Momentum: %10
- Destek/direnç: %15

Ağırlıklar konfigüre edilebilir olmalı. Her bileşen -100..+100 normalize edilmiş katkı ve nihai 0..100 skor üretebilir. Skor, gerçek veri ve yeterli geçmiş olmadan üretilmemeli. Skorun açıklaması, hesaplama zamanı, veri aralığı ve algoritma versiyonu saklanmalı.

## UX ilkeleri
- Finansal terminal hissi veren, sade ve okunaklı arayüz.
- Koyu/açık tema.
- Mobilde kullanılabilir responsive tasarım.
- Renk tek başına anlam taşımamalı; ikon/metin ile desteklenmeli.
- Grafiklerde veri kaynağı ve son güncellenme zamanı görünmeli.
- Yatırım tavsiyesi değildir uyarısı uygun alanlarda görünmeli.
