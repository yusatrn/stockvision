import type { ExchangeResponse, InstrumentType } from "@/lib/api/types";

export interface BistInstrumentRecord {
  id: string;
  symbol: string;
  name: string;
  exchange: string;
  sector: string | null;
  currency: string;
  type: InstrumentType;
  active: boolean;
  catalogRank: number;
}

export const BIST_EXCHANGE: ExchangeResponse = {
  id: "11111111-1111-1111-1111-000000000001",
  code: "BIST",
  name: "Borsa İstanbul",
  countryCode: "TR",
  currency: "TRY",
  timezone: "Europe/Istanbul",
  sessionOpenLocal: "10:00",
  sessionCloseLocal: "18:00",
  active: true,
};

const RAW_BIST_SEEDS: Array<[string, string, string]> = [
  ["ASELS", "ASELSAN ELEKTRONİK SANAYİ VE TİCARET A.Ş.", "Electronic Technology"],
  ["QNBTR", "QNB Bank AS", "Finance"],
  ["TUPRS", "TÜPRAŞ-TÜRKİYE PETROL RAFİNERİLERİ A.Ş.", "Energy Minerals"],
  ["GARAN", "TÜRKİYE GARANTİ BANKASI A.Ş.", "Finance"],
  ["KCHOL", "KOÇ HOLDİNG A.Ş.", "Holdings"],
  ["ENKAI", "ENKA İNŞAAT VE SANAYİ A.Ş.", "Industrial Services"],
  ["BIMAS", "BİM BİRLEŞİK MAĞAZALAR A.Ş.", "Retail Trade"],
  ["THYAO", "TÜRK HAVA YOLLARI A.O.", "Transportation"],
  ["AKBNK", "AKBANK T.A.Ş.", "Finance"],
  ["ISBTR", "TÜRKİYE İŞ BANKASI A.Ş. (B)", "Finance"],
  ["ISCTR", "TÜRKİYE İŞ BANKASI A.Ş.", "Finance"],
  ["VAKBN", "TÜRKİYE VAKIFLAR BANKASI T.A.O.", "Finance"],
  ["YKBNK", "YAPI VE KREDİ BANKASI A.Ş.", "Finance"],
  ["HALKB", "TÜRKİYE HALK BANKASI A.Ş.", "Finance"],
  ["FROTO", "FORD OTOMOTİV SANAYİ A.Ş.", "Consumer Durables"],
  ["CCOLA", "COCA-COLA İÇECEK A.Ş.", "Consumer Non-Durables"],
  ["EREGL", "EREĞLİ DEMİR VE ÇELİK FABRİKALARI T.A.Ş.", "Non-Energy Minerals"],
  ["TCELL", "TURKCELL İLETİŞİM HİZMETLERİ A.Ş.", "Communications"],
  ["TTKOM", "TÜRK TELEKOMÜNİKASYON A.Ş.", "Communications"],
  ["SAHOL", "HACI ÖMER SABANCI HOLDİNG A.Ş.", "Holdings"],
  ["ASTOR", "ASTOR ENERJİ A.Ş.", "Producer Manufacturing"],
  ["ISDMR", "İSKENDERUN DEMİR VE ÇELİK A.Ş.", "Non-Energy Minerals"],
  ["DOCO", "DO & CO AKTIENGESELLSCHAFT", "Consumer Services"],
  ["INVES", "INVESTCO HOLDİNG A.Ş.", "Finance"],
  ["ENJSA", "ENERJİSA ENERJİ A.Ş.", "Utilities"],
  ["ENERY", "Enerya Enerji A.S.", "Utilities"],
  ["TOASO", "TOFAŞ TÜRK OTOMOBİL FABRİKASI A.Ş.", "Consumer Durables"],
  ["AHGAZ", "AHLATCI DOĞAL GAZ DAĞITIM ENERJİ VE YATIRIM A.Ş.", "Utilities"],
  ["TRALT", "Turk Altin Isletmeleri A.S.", "Non-Energy Minerals"],
  ["TAVHL", "TAV HAVALİMANLARI HOLDİNG A.Ş.", "Transportation"],
  ["SISE", "TÜRKİYE ŞİŞE VE CAM FABRİKALARI A.Ş.", "Consumer Durables"],
  ["AEFES", "ANADOLU EFES BİRACILIK VE MALT SANAYİİ A.Ş.", "Consumer Non-Durables"],
  ["RYGYO", "REYSAŞ GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş.", "Finance"],
  ["GUBRF", "GÜBRE FABRİKALARI T.A.Ş.", "Process Industries"],
  ["OYAKC", "OYAK ÇİMENTO FABRİKALARI A.Ş.", "Non-Energy Minerals"],
  ["BRSAN", "BORUSAN MANNESMANN BORU SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["TRGYO", "TORUNLAR GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş.", "Finance"],
  ["MPARK", "MLP SAĞLIK HİZMETLERİ A.Ş.", "Health Services"],
  ["AKSEN", "AKSA ENERJİ ÜRETİM A.Ş.", "Utilities"],
  ["QNBFK", "QNB Finansal Kiralama A.S.", "Finance"],
  ["MGROS", "MİGROS TİCARET A.Ş.", "Retail Trade"],
  ["ZRGYO", "ZİRAAT GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş.", "Finance"],
  ["SASA", "SASA POLYESTER SANAYİ A.Ş.", "Process Industries"],
  ["PGSUS", "PEGASUS HAVA TAŞIMACILIĞI A.Ş.", "Transportation"],
  ["KENT", "KENT GIDA MADDELERİ SANAYİİ VE TİCARET A.Ş.", "Consumer Non-Durables"],
  ["KLNMA", "TÜRKİYE KALKINMA VE YATIRIM BANKASI A.Ş.", "Finance"],
  ["TKFEN", "TEKFEN HOLDİNG A.Ş.", "Industrial Services"],
  ["EKGYO", "EMLAK KONUT GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş.", "Finance"],
  ["AGHOL", "AG ANADOLU GRUBU HOLDİNG A.Ş.", "Consumer Non-Durables"],
  ["TURSG", "TÜRKİYE SİGORTA A.Ş.", "Finance"],
  ["SELEC", "SELÇUK ECZA DEPOSU TİCARET VE SANAYİ A.Ş.", "Distribution Services"],
  ["AYGAZ", "AYGAZ A.Ş.", "Utilities"],
  ["KTLEV", "KATILIMEVİM TASARRUF FİNANSMAN A.Ş.", "Finance"],
  ["RGYAS", "RÖNESANS GAYRİMENKUL YATIRIM A.Ş.", "Finance"],
  ["RYSAS", "REYSAŞ TAŞIMACILIK VE LOJİSTİK TİCARET A.Ş.", "Transportation"],
  ["TABGD", "TAB Gıda Sanayi ve Ticaret A.Ş.", "Consumer Services"],
  ["CVKMD", "CVK MADEN İŞLETMELERİ SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["ARCLK", "ARÇELİK A.Ş.", "Consumer Durables"],
  ["ANSGR", "ANADOLU ANONİM TÜRK SİGORTA ŞİRKETİ", "Finance"],
  ["ALARK", "ALARKO HOLDİNG A.Ş.", "Holdings"],
  ["AKSA", "AKSA AKRİLİK KİMYA SANAYİİ A.Ş.", "Process Industries"],
  ["ANHYT", "ANADOLU HAYAT EMEKLİLİK A.Ş.", "Finance"],
  ["ISMEN", "İŞ YATIRIM MENKUL DEĞERLER A.Ş.", "Finance"],
  ["KRDMD", "KARDEMİR KARABÜK DEMİR ÇELİK SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["PETKM", "PETKİM PETROKİMYA HOLDİNG A.Ş.", "Process Industries"],
  ["DOHOL", "DOĞAN ŞİRKETLER GRUBU HOLDİNG A.Ş.", "Holdings"],
  ["OTKAR", "OTOKAR OTOMOTİV VE SAVUNMA SANAYİ A.Ş.", "Consumer Durables"],
  ["TBORG", "TÜRK TUBORG BİRA VE MALT SANAYİİ A.Ş.", "Consumer Non-Durables"],
  ["CIMSA", "ÇİMSA ÇİMENTO SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["AGESA", "AGESA HAYAT VE EMEKLİLİK A.Ş.", "Finance"],
  ["VERUS", "VERUSA HOLDİNG A.Ş.", "Finance"],
  ["AKCNS", "AKÇANSA ÇİMENTO SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["TTRAK", "TÜRK TRAKTÖR VE ZİRAAT MAKİNELERİ A.Ş.", "Producer Manufacturing"],
  ["BRYAT", "BORUSAN YATIRIM VE PAZARLAMA A.Ş.", "Holdings"],
  ["ECILC", "EİS ECZACIBAŞI İLAÇ SINAİ VE FİNANSAL YATIRIMLAR A.Ş.", "Health Technology"],
  ["CLEBI", "ÇELEBİ HAVA SERVİSİ A.Ş.", "Transportation"],
  ["GLYHO", "GLOBAL YATIRIM HOLDİNG A.Ş.", "Finance"],
  ["PASEU", "Pasifik Eurasia Lojistik Dış Ticaret A.Ş.", "Transportation"],
  ["TSKB", "TÜRKİYE SINAİ KALKINMA BANKASI A.Ş.", "Finance"],
  ["MAVI", "MAVİ GİYİM SANAYİ VE TİCARET A.Ş.", "Consumer Non-Durables"],
  ["BASGZ", "BAŞKENT DOĞALGAZ DAĞITIM GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş.", "Utilities"],
  ["ULKER", "ÜLKER BİSKÜVİ SANAYİ A.Ş.", "Consumer Non-Durables"],
  ["GRSEL", "GÜR-SEL TURİZM TAŞIMACILIK VE SERVİS TİCARET A.Ş.", "Consumer Services"],
  ["SOKM", "ŞOK MARKETLER TİCARET A.Ş.", "Retail Trade"],
  ["NUHCM", "NUH ÇİMENTO SANAYİ A.Ş.", "Non-Energy Minerals"],
  ["EUPWR", "EUROPOWER ENERJİ VE OTOMASYON TEKNOLOJİLERİ SANAYİ TİCARET A.Ş.", "Producer Manufacturing"],
  ["MIATK", "MİA TEKNOLOJİ A.Ş.", "Technology Services"],
  ["DOAS", "DOĞUŞ OTOMOTİV SERVİS VE TİCARET A.Ş.", "Retail Trade"],
  ["YEOTK", "YEO TEKNOLOJİ ENERJİ VE ENDÜSTRİ A.Ş.", "Utilities"],
  ["GENIL", "GEN İLAÇ VE SAĞLIK ÜRÜNLERİ SANAYİ VE TİCARET A.Ş.", "Health Technology"],
  ["ALBRK", "ALBARAKA TÜRK KATILIM BANKASI A.Ş.", "Finance"],
  ["AKFYE", "AKFEN YENİLENEBİLİR ENERJİ A.Ş.", "Utilities"],
  ["BRISA", "BRİSA BRIDGESTONE SABANCI LASTİK SANAYİ VE TİCARET A.Ş.", "Consumer Durables"],
  ["KONTR", "KONTROLMATİK TEKNOLOJİ ENERJİ VE MÜHENDİSLİK A.Ş.", "Electronic Technology"],
  ["HEKTS", "HEKTAŞ TİCARET T.A.Ş.", "Process Industries"],
  ["KOZAL", "KOZA ALTIN İŞLETMELERİ A.Ş.", "Non-Energy Minerals"],
  ["VESTL", "VESTEL ELEKTRONİK SANAYİ VE TİCARET A.Ş.", "Consumer Durables"],
  ["ODAS", "ODAŞ ELEKTRİK ÜRETİM SANAYİ TİCARET A.Ş.", "Utilities"],
  ["GESAN", "GİRİŞİM ELEKTRİK SANAYİ TAAHHÜT VE TİCARET A.Ş.", "Producer Manufacturing"],
  ["SMRTG", "SMART GÜNEŞ ENERJİSİ TEKNOLOJİLERİ AR-GE ÜRETİM A.Ş.", "Producer Manufacturing"],
  ["LOGO", "LOGO YAZILIM SANAYİ VE TİCARET A.Ş.", "Technology Services"],
  ["ARDYZ", "ARD GRUP BİLİŞİM TEKNOLOJİLERİ A.Ş.", "Technology Services"],
  ["REEDR", "REEDER TEKNOLOJİ SANAYİ VE TİCARET A.Ş.", "Electronic Technology"],
  ["CWENE", "CW ENERJİ MÜHENDİSLİK TİCARET VE SANAYİ A.Ş.", "Producer Manufacturing"],
  ["ALFAS", "ALFA SOLAR ENERJİ SANAYİ VE TİCARET A.Ş.", "Producer Manufacturing"],
  ["CANTE", "ÇAN2 TERMİK A.Ş.", "Utilities"],
  ["ZOREN", "ZORLU ENERJİ ELEKTRİK ÜRETİM A.Ş.", "Utilities"],
  ["KOZAA", "KOZA ANADOLU METAL MADENCİLİK İŞLETMELERİ A.Ş.", "Non-Energy Minerals"],
  ["IPEKE", "İPEK DOĞAL ENERJİ KAYNAKLARI ARAŞTIRMA VE ÜRETİM A.Ş.", "Energy Minerals"],
  ["BERA", "BERA HOLDİNG A.Ş.", "Holdings"],
  ["KCAER", "KOCAER ÇELİK SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["BFREN", "BOSCH FREN SİSTEMLERİ SANAYİ VE TİCARET A.Ş.", "Consumer Durables"],
  ["EGEEN", "EGE ENDÜSTRİ VE TİCARET A.Ş.", "Consumer Durables"],
  ["GOLTS", "GÖLTAŞ GÖLLER BÖLGESİ ÇİMENTO SANAYİ VE TİCARET A.Ş.", "Non-Energy Minerals"],
  ["ISGYO", "İŞ GAYRİMENKUL YATIRIM ORTAKLIĞI A.Ş.", "Finance"],
  ["SKBNK", "ŞEKERBANK T.A.Ş.", "Finance"],
  ["TKNSA", "TEKNOSA İÇ VE DIŞ TİCARET A.Ş.", "Retail Trade"],
  ["VESBE", "VESTEL BEYAZ EŞYA SANAYİ VE TİCARET A.Ş.", "Consumer Durables"],
  ["YATAS", "YATAŞ YATAK VE YORGAN SANAYİ VE TİCARET A.Ş.", "Consumer Durables"],
  ["KONYA", "KONYA ÇİMENTO SANAYİİ A.Ş.", "Non-Energy Minerals"],
];

export const BIST_INSTRUMENTS: BistInstrumentRecord[] = RAW_BIST_SEEDS.map(
  ([symbol, name, sector], idx) => ({
    id: `22222222-2222-2222-2222-${String(101 + idx).padStart(12, "0")}`,
    symbol,
    name,
    exchange: "BIST",
    sector,
    currency: "TRY",
    type: "EQUITY",
    active: true,
    catalogRank: idx + 1,
  })
);

const BY_SYMBOL = new Map<string, BistInstrumentRecord>(
  BIST_INSTRUMENTS.map((inst) => [inst.symbol, inst])
);

export function normalizeSymbol(symbol: string): string {
  return symbol.trim().toUpperCase().replace(/\.IS$/i, "");
}

export function findInstrument(symbol: string): BistInstrumentRecord | undefined {
  return BY_SYMBOL.get(normalizeSymbol(symbol));
}
