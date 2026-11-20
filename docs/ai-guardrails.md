# StockVision AI — Yapay Zekâ Açıklama Modülü ve Guardrail Mimarisi (Faz 8)

Bu doküman StockVision AI içindeki isteğe bağlı doğal dil açıklama modülünün (`com.stockvision.ai`) ücretsiz AI sağlayıcı politikasını, gerçek-veri bağlama (fact grounding) kurallarını, JSON şema/uyumluluk doğrulamasını ve deterministik şablon yedekleme (fallback) mekanizmasını açıklar.

> **Bilgilendirme ve Sorumluluk Reddi:** Üretilen doğal dil açıklamaları (hem AI sağlayıcıları hem de deterministik şablon çıktıları) yalnızca bilgilendirme ve karar destek amaçlıdır; hata veya eksiklik içerebilir, kişiye özel yatırım tavsiyesi değildir ve otomatik emir üretmez.

---

## 1. Ücretsiz AI Sağlayıcı Politikası (`AiExplanationProvider`)

Proje ilkesi gereği harici ücretli AI API'leri kullanılmaz. Tüm sağlayıcılar `AiExplanationProvider` arayüzü arkasındadır ve konfigürasyon ortam değişkenlerinden okunur (`app.ai.provider`):

| Değer (`AI_PROVIDER`) | Sağlayıcı | Kimlik Bilgisi | Maliyet |
|---|---|---|---|
| `template` *(varsayılan)* | `DeterministicTemplateAiProvider` (`template-v1.0.0`) | Gerekmez | Ücretsiz (yerel deterministik kural) |
| `ollama` | `OllamaAiExplanationProvider` (ör. `llama3.2:3b`) | Yerel HTTP uç noktası (`AI_OLLAMA_BASE_URL`) | Ücretsiz (yerel açık kaynak LLM) |
| `gemini-free` | `GeminiFreeAiExplanationProvider` (ör. `gemini-2.0-flash-lite`) | Kullanıcının sağlayacağı ücretsiz katman `AI_GEMINI_API_KEY` (`x-goog-api-key` başlığında gönderilir; URL veya loglara yazılmaz) | Ücretsiz katman |

---

## 2. Veri İzolasyonu ve Gerçeklere Bağlama (Fact Grounding)

- **Yalnızca Kalıcı Analiz Gerçekleri (`AiFactPack`):** AI katmanı portföy (`portfolio`), takip listesi (`watchlist`), alarm (`alert`) veya kullanıcı (`user`) modüllerine erişmez. Sağlayıcıya yalnızca kalıcı teknik analiz/skor koşularından (`technical_score_runs`, `analysis_runs`) türetilmiş sayısal metrikler, indikatör bileşenleri ve piyasa veri kaynağı/tazelik üst verileri gönderilir.
- **Kişisel Portföy Kısıtı:** Kişisel portföy veya kimlik bilgileri açık ihtiyaç ve onay olmadan asla harici AI sağlayıcılarına gönderilmez.
- **Sürümleştirilmiş Prompt (`ai-explain-v1.0.0`):** Sistem prompt'u uydurma fiyat, indikatör, haber veya temel analiz verisini yasaklar; çıktıyı **Observed Facts** (`observedFacts`), **Technical Interpretation** (`interpretation`) ve **Uncertainties & Risks** (`uncertaintiesAndRisks`) olarak üç net bölüme ayırmayı zorunlu kılar.

---

## 3. Şema Doğrulaması, Uyumluluk Guardrail ve Fallback Matrisi

Ham model çıktısı `ai_explanations` tablosuna yazılmadan veya istemciye dönülmeden önce `AiOutputValidator` tarafından sıkı denetimden geçirilir:

| Senaryo / Hata | Tespit Mekanizması | `status` | `fallbackUsed` | `fallbackReason` |
|---|---|---|---|---|
| `AI_PROVIDER=template` | Doğrudan `DeterministicTemplateExplanationGenerator` | `GENERATED` | `false` | `TEMPLATE_MODE_SELECTED` |
| Skor durumu `INSUFFICIENT_DATA` veya `staleData=true` | Harici AI çağrısı yapılmadan kısa devre (zero hallucination) | `INSUFFICIENT_DATA` | Dış sağlayıcı seçiliyse `true` | `INSUFFICIENT_DATA_SHORT_CIRCUIT` |
| Harici API anahtarı / URL eksik | `isConfigured() == false` | `FALLBACK` | `true` | `PROVIDER_UNCONFIGURED` |
| Sağlayıcı zaman aşımı (`AI_TIMEOUT_MS`) | `HttpTimeoutException` | `FALLBACK` | `true` | `PROVIDER_TIMEOUT` |
| Sağlayıcı erişilemiyor / HTTP != 2xx | `IOException` / HTTP durum kontrolü | `FALLBACK` | `true` | `PROVIDER_UNAVAILABLE` |
| Bozuk / JSON olmayan çıktı | Jackson `JsonProcessingException` | `FALLBACK` | `true` | `MALFORMED_OUTPUT` |
| Zorunlu alan eksikliği veya sınır ihlali | `summary` (15–800), diziler, tip ve uzunluk sınırları | `FALLBACK` | `true` | `SCHEMA_VALIDATION_FAILED` |
| Yatırım tavsiyesi veya kesin kâr garantisi ifadesi | Yasaklı ifade denetimi (EN/TR) | `FALLBACK` | `true` | `GUARDRAIL_VIOLATION` |

---

## 4. Denetim Kaydı (`ai_explanations`)

Her üretilen veya fallback ile sunulan açıklama [V7__create_ai_explanations_table.sql](file:///f:/proje/stockvision/backend/src/main/resources/db/migration/V7__create_ai_explanations_table.sql) içindeki `ai_explanations` tablosuna şu alanlarla kaydedilir:
- `analysis_run_id` ve `score_run_id`
- `provider_id`, `requested_provider_id` ve `model_name`
- `prompt_version` (`ai-explain-v1.0.0`)
- `status`, `fallback_used`, `fallback_reason`
- `simulated_data`, `stale_data`
- `generated_at` (UTC) ve `disclaimer`
