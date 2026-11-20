/**
 * Merkezi Türkçe etiket ve enum çeviri yardımcıları.
 * Backend API sözleşme enum değerleri korunur, kullanıcı arayüzünde doğal Türkçe karşılıkları gösterilir.
 */

const SECTOR_MAP: Record<string, string> = {
  Transportation: "Ulaştırma",
  Banking: "Bankacılık",
  "Defense & Electronics": "Savunma ve Elektronik",
  Retail: "Perakende",
  "Basic Materials": "Ana Metal ve Madencilik",
  Holdings: "Holding",
  Industrials: "Sanayi",
  Energy: "Enerji",
  Finance: "Finans ve Bankacılık",
  "Process Industries": "Proses Sanayii",
  "Producer Manufacturing": "Üretim ve İmalat",
  "Consumer Non-Durables": "Dayanıksız Tüketim",
  "Non-Energy Minerals": "Enerji Dışı Madenler",
  Utilities: "Altyapı ve Enerji Dağıtımı",
  "Consumer Services": "Tüketici Hizmetleri",
  "Consumer Durables": "Dayanıklı Tüketim",
  "Technology Services": "Teknoloji Hizmetleri",
  "Distribution Services": "Dağıtım ve Toptan Ticaret",
  "Retail Trade": "Perakende Ticaret",
  "Electronic Technology": "Elektronik Teknolojisi",
  "Commercial Services": "Ticari Hizmetler",
  "Industrial Services": "Endüstriyel Hizmetler",
  "Health Technology": "Sağlık Teknolojileri",
  Miscellaneous: "Çeşitli / Diğer",
  "Health Services": "Sağlık Hizmetleri",
  "Energy Minerals": "Enerji ve Rafineri",
  Communications: "İletişim ve Telekomünikasyon",
};

const INSTRUMENT_TYPE_MAP: Record<string, string> = {
  EQUITY: "Hisse Senedi",
  ETF: "BYF",
  INDEX: "Endeks",
};

const SCORE_COMPONENT_MAP: Record<string, string> = {
  TREND: "Trend",
  RSI: "RSI",
  MACD: "MACD",
  VOLUME: "Hacim",
  BOLLINGER: "Bollinger Bantları",
  MOMENTUM: "Momentum",
  SUPPORT_RESISTANCE: "Destek / Direnç",
};

const INDICATOR_NAME_MAP: Record<string, string> = {
  SMA: "SMA (Basit Hareketli Ort.)",
  EMA: "EMA (Üssel Hareketli Ort.)",
  RSI: "RSI (Göreceli Güç Endeksi)",
  MACD: "MACD",
  BOLLINGER: "Bollinger Bantları",
  STOCHASTIC: "Stokastik Osilatör",
  ATR: "ATR (Ortalama Gerçek Aralık)",
  VOLUME_AVG: "Hacim Ort. ve Oranı",
};

const INDICATOR_FIELD_MAP: Record<string, string> = {
  value: "Değer",
  macd: "MACD",
  signal: "Sinyal",
  histogram: "Histogram",
  upper: "Üst Bant",
  middle: "Orta Bant",
  lower: "Alt Bant",
  bandwidth: "Bant Genişliği",
  k: "%K",
  d: "%D",
  average: "Ortalama",
  ratio: "Oran",
  close: "Kapanış",
  sma: "SMA",
  ema: "EMA",
  emaSlope5BarPct: "5 Barlık EMA Eğimi (%)",
  rsi: "RSI",
  averageVolume: "Ortalama Hacim",
  volumeRatio: "Hacim Oranı",
  percentB: "%B",
  roc10Pct: "10 Barlık ROC (%)",
  stochasticK: "Stokastik %K",
  stochasticD: "Stokastik %D",
  support20: "20 Barlık Destek",
  resistance20: "20 Barlık Direnç",
  rangePosition: "Aralık Konumu",
};

const ALERT_CONDITION_MAP: Record<string, string> = {
  PRICE_ABOVE: "Fiyat Eşit veya Üstünde",
  PRICE_BELOW: "Fiyat Eşit veya Altında",
  INDICATOR_ABOVE: "İndikatör Eşit veya Üstünde",
  INDICATOR_BELOW: "İndikatör Eşit veya Altında",
};

const ALERT_STATUS_MAP: Record<string, string> = {
  ACTIVE: "AKTİF",
  PAUSED: "DURAKLATILDI",
};

const TRIGGER_STATE_MAP: Record<string, string> = {
  ARMED: "BEKLEMEDE",
  TRIGGERED: "TETİKLENDİ",
};

const TRANSACTION_SIDE_MAP: Record<string, string> = {
  BUY: "ALIŞ",
  SELL: "SATIŞ",
};

const FEE_POLICY_MAP: Record<string, string> = {
  EXCLUDED_MVP: "MVP Kapsamı Dışında (0 Komisyon/Vergi)",
  EXPLICIT_PER_TXN: "İşlem Başına Açık Giriş",
};

const AI_STATUS_MAP: Record<string, string> = {
  GENERATED: "ÜRETİLDİ",
  FALLBACK: "ŞABLON YEDEĞİ",
  INSUFFICIENT_DATA: "Yetersiz Veri (INSUFFICIENT_DATA)",
};

const AI_PROVIDER_MAP: Record<string, string> = {
  "deterministic-template": "Deterministik Türkçe Şablon",
  ollama: "Yerel Ollama (Ücretsiz LLM)",
  "gemini-free": "Google Gemini (Ücretsiz Katman)",
};

const FALLBACK_REASON_MAP: Record<string, string> = {
  TEMPLATE_MODE_SELECTED: "Şablon modu seçili",
  PROVIDER_NOT_CONFIGURED: "Dış sağlayıcı yapılandırılmadı",
  PROVIDER_TIMEOUT: "Sağlayıcı zaman aşımı",
  PROVIDER_UNAVAILABLE: "Sağlayıcı erişilemiyor",
  GUARDRAIL_VIOLATION: "Güvenlik/uyum kuralı ihlali",
  MALFORMED_OUTPUT: "Geçersiz JSON çıktısı",
  SCHEMA_VALIDATION_FAILED: "Şema doğrulaması başarısız",
  INSUFFICIENT_DATA: "Yetersiz veya bayat veri",
};

const SYNC_STATUS_MAP: Record<string, string> = {
  SUCCESS: "BAŞARILI",
  FAILED: "BAŞARISIZ",
};

const VALUATION_STATUS_MAP: Record<string, string> = {
  READY: "HAZIR",
  PARTIAL: "KISMİ",
  EMPTY: "BOŞ",
  UNAVAILABLE: "KULLANILAMIYOR",
};

const SETUP_CATEGORY_MAP: Record<string, string> = {
  HIGH_MOMENTUM_BREAKOUT: "Yüksek Momentum / Kırılım Adayı",
  PULLBACK_WATCH: "Destek / Geri Çekilme İzleme",
  OVEREXTENDED_RISK: "Aşırı Alım / Isınma Riski",
  CONSOLIDATION: "Yatay / Sıkışma Bölgesi",
  WEAK_BEARISH: "Zayıf / Düşüş Eğilimli",
  INSUFFICIENT_DATA: "Yetersiz Veri (INSUFFICIENT_DATA)",
};

export function translateSector(sector: string | null | undefined): string {
  if (!sector) {
    return "—";
  }
  return SECTOR_MAP[sector] ?? sector;
}

export function translateInstrumentType(type: string | null | undefined): string {
  if (!type) {
    return "—";
  }
  return INSTRUMENT_TYPE_MAP[type] ?? type;
}

export function translateScoreComponent(type: string): string {
  return SCORE_COMPONENT_MAP[type] ?? type.replaceAll("_", " ");
}

export function translateIndicator(type: string): string {
  return INDICATOR_NAME_MAP[type] ?? type;
}

export function translateIndicatorField(field: string): string {
  return INDICATOR_FIELD_MAP[field] ?? field;
}

export function translateAlertCondition(condition: string): string {
  return ALERT_CONDITION_MAP[condition] ?? condition;
}

export function translateAlertStatus(status: string): string {
  return ALERT_STATUS_MAP[status] ?? status;
}

export function translateTriggerState(state: string): string {
  return TRIGGER_STATE_MAP[state] ?? state;
}

export function translateTransactionSide(side: string): string {
  return TRANSACTION_SIDE_MAP[side] ?? side;
}

export function translateFeePolicy(policy: string): string {
  return FEE_POLICY_MAP[policy] ?? policy;
}

export function translateAiStatus(status: string): string {
  return AI_STATUS_MAP[status] ?? status;
}

export function translateAiProvider(providerId: string): string {
  return AI_PROVIDER_MAP[providerId] ?? providerId;
}

export function translateFallbackReason(reason: string | null | undefined): string {
  if (!reason) {
    return "Kullanılamıyor";
  }
  return FALLBACK_REASON_MAP[reason] ?? reason;
}

export function translateSyncStatus(status: string | null | undefined): string {
  if (!status) {
    return "Yok";
  }
  return SYNC_STATUS_MAP[status] ?? status;
}

export function translateValuationStatus(status: string): string {
  return VALUATION_STATUS_MAP[status] ?? status;
}

export function translateSetupCategory(category: string): string {
  return SETUP_CATEGORY_MAP[category] ?? category.replaceAll("_", " ");
}

export function translateProviderMode(mode: string): string {
  switch (mode) {
    case "SIMULATED":
      return "Simüle Veri";
    case "DELAYED":
      return "Gecikmeli Veri";
    case "REALTIME":
      return "Gerçek Zamanlı";
    case "UNCONFIGURED":
      return "Yapılandırılmamış";
    default:
      return mode;
  }
}
