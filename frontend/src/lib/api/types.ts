export type DataProviderMode =
  | "SIMULATED"
  | "DELAYED"
  | "REALTIME"
  | "UNCONFIGURED";

export type FreshnessStatus =
  | "FRESH"
  | "STALE"
  | "SIMULATED_ONLY"
  | "NO_DATA";

export type SyncStatus = "SUCCESS" | "FAILED";
export type UserRole = "USER" | "ADMIN";
export type UserStatus = "ACTIVE" | "SUSPENDED" | "LOCKED";
export type InstrumentType = "EQUITY" | "ETF" | "INDEX";

export type IndicatorType =
  | "SMA"
  | "EMA"
  | "RSI"
  | "MACD"
  | "BOLLINGER"
  | "STOCHASTIC"
  | "ATR"
  | "VOLUME_AVG";

export type IndicatorSeriesStatus = "READY" | "INSUFFICIENT_DATA";
export type TechnicalAnalysisStatus = "READY" | "PARTIAL" | "INSUFFICIENT_DATA";
export type ScoreStatus = "READY" | "INSUFFICIENT_DATA";

export type ScoreRegime =
  | "STRONG_BULLISH"
  | "BULLISH"
  | "NEUTRAL"
  | "BEARISH"
  | "STRONG_BEARISH"
  | "INSUFFICIENT_DATA"
  | (string & {});

export type TrendDirection =
  | "BULLISH"
  | "NEUTRAL"
  | "BEARISH"
  | "INSUFFICIENT_DATA"
  | (string & {});

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  traceId: string;
  fieldErrors?: ApiFieldError[];
}

export interface HealthResponse {
  status: string;
  service: string;
  timestamp: string;
  dataProviderMode: DataProviderMode;
  disclaimer: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName?: string;
  timezone?: string;
}

export interface RegisterResponse {
  status: string;
  message: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserProfileResponse {
  id: string;
  email: string;
  displayName: string | null;
  role: UserRole;
  status: UserStatus;
  timezone: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  expiresAt: string;
  user: UserProfileResponse;
  refreshToken?: string;
  refreshExpiresAt?: string;
}

export interface DataSourceMetadataResponse {
  providerId: string;
  displayName: string;
  mode: DataProviderMode;
  simulated: boolean;
  delayMinutes: number;
  sourceTimezone: string;
  licenseNote: string;
}

export interface FreshnessMetadataResponse {
  status: FreshnessStatus;
  stale: boolean;
  simulated: boolean;
  latestDataTimestamp: string | null;
  lastSyncedAt: string | null;
  lastSyncStatus: SyncStatus | null;
  ageMinutes: number | null;
  note: string;
}

export interface ExchangeResponse {
  id: string;
  code: string;
  name: string;
  countryCode: string;
  currency: string;
  timezone: string;
  sessionOpenLocal: string;
  sessionCloseLocal: string;
  active: boolean;
}

export interface MarketMoverResponse {
  symbol: string;
  name: string;
  price: number;
  changeAmount: number;
  changePercent: number;
  volume: number;
  currency: string;
  quotedAt: string;
  simulated: boolean;
}

export interface MarketSummaryResponse {
  exchange: ExchangeResponse;
  activeInstrumentCount: number;
  simulated: boolean;
  summaryNote: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  movers: MarketMoverResponse[];
}

export interface InstrumentSummaryResponse {
  id: string;
  symbol: string;
  name: string;
  exchange: string;
  sector: string | null;
  currency: string;
  type: InstrumentType;
  active: boolean;
}

export interface InstrumentPageResponse {
  items: InstrumentSummaryResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  dataSource: DataSourceMetadataResponse;
}

export interface QuoteSnapshotResponse {
  price: number;
  previousClose: number;
  changeAmount: number;
  changePercent: number;
  volume: number;
  currency: string;
  quotedAt: string;
  provider: string;
  simulated: boolean;
  delayMinutes: number;
  sourceTimezone: string;
}

export interface InstrumentDetailResponse {
  id: string;
  symbol: string;
  name: string;
  exchangeCode: string;
  exchangeName: string;
  exchangeTimezone: string;
  sector: string | null;
  currency: string;
  type: InstrumentType;
  active: boolean;
  latestQuote: QuoteSnapshotResponse;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
}

export interface PriceBarResponse {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  currency: string;
  interval: string;
  provider: string;
  adjusted: boolean;
  simulated: boolean;
  sourceTimezone: string;
  ingestedAt: string;
}

export interface HistoricalBarsResponse {
  symbol: string;
  exchange: string;
  interval: string;
  adjusted: boolean;
  from: string;
  to: string;
  count: number;
  missingDataNote: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  bars: PriceBarResponse[];
}

export interface IndicatorPointResponse {
  timestamp: string;
  warmUp: boolean;
  values: Record<string, number | null>;
}

export interface IndicatorSeriesResponse {
  type: IndicatorType;
  status: IndicatorSeriesStatus;
  warmUpBars: number;
  requiredBars: number;
  availableBars: number;
  parameters: Record<string, unknown>;
  latestValues: Record<string, number | null>;
  points: IndicatorPointResponse[];
}

export interface TechnicalIndicatorsResponse {
  analysisRunId: string;
  symbol: string;
  exchange: string;
  interval: string;
  adjusted: boolean;
  from: string;
  to: string;
  barCount: number;
  engineVersion: string;
  status: TechnicalAnalysisStatus;
  calculatedAt: string;
  disclaimer: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  indicators: IndicatorSeriesResponse[];
}

export interface ScoreComponentResponse {
  type: string;
  status: ScoreStatus;
  weightPercent: number;
  normalizedScore: number | null;
  weightedContribution: number | null;
  explanation: string;
  metrics: Record<string, number | null>;
}

export interface AnalysisExplanationResponse {
  headline: string;
  keyDrivers: string[];
  riskAndDataNotes: string[];
  methodologySummary: string;
  disclaimer: string;
}

export interface InstrumentAnalysisResponse {
  scoreRunId: string;
  analysisRunId: string;
  symbol: string;
  exchange: string;
  interval: string;
  adjusted: boolean;
  from: string;
  to: string;
  barCount: number;
  algorithmVersion: string;
  indicatorEngineVersion: string;
  status: ScoreStatus;
  staleData: boolean;
  finalScore: number | null;
  regime: ScoreRegime;
  trendDirection: TrendDirection;
  latestClose: number | null;
  latestRsi: number | null;
  insufficientDataReasons: string[];
  weights: Record<string, number>;
  components: ScoreComponentResponse[];
  explanation: AnalysisExplanationResponse;
  calculatedAt: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
}

export interface ScreenerItemResponse {
  scoreRunId: string;
  symbol: string;
  name: string;
  exchange: string;
  sector: string | null;
  currency: string;
  status: ScoreStatus;
  finalScore: number | null;
  regime: ScoreRegime;
  trendDirection: TrendDirection;
  latestClose: number | null;
  latestRsi: number | null;
  simulated: boolean;
  staleData: boolean;
  headline: string;
  calculatedAt: string;
}

export interface ScreenerPageResponse {
  exchange: string;
  interval: string;
  adjusted: boolean;
  algorithmVersion: string;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  disclaimer: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  items: ScreenerItemResponse[];
}

export interface InstrumentListParams {
  query?: string;
  exchange?: string;
  page?: number;
  size?: number;
}

export interface HistoricalBarsParams {
  interval?: string;
  from?: string;
  to?: string;
  adjusted?: boolean;
}

export interface TechnicalIndicatorsParams {
  interval?: string;
  indicators?: IndicatorType[];
  from?: string;
  to?: string;
  adjusted?: boolean;
}

export interface AnalysisQueryParams {
  interval?: string;
  from?: string;
  to?: string;
  adjusted?: boolean;
}

export type UniverseScope = "BIST30" | "BIST100" | "SYNCED" | "ALL";

export interface ScreenerQueryParams {
  exchange?: string;
  interval?: string;
  scope?: UniverseScope;
  query?: string;
  minScore?: number;
  maxScore?: number;
  rsiMin?: number;
  rsiMax?: number;
  trend?: string;
  includeInsufficientData?: boolean;
  sortBy?: "score" | "symbol" | "rsi";
  sortDir?: "asc" | "desc";
  page?: number;
  size?: number;
}

export interface WatchlistItemQuoteResponse {
  price: number;
  changeAmount: number;
  changePercent: number;
  volume: number;
  currency: string;
  quotedAt: string;
  simulated: boolean;
}

export interface WatchlistItemResponse {
  id: string;
  instrumentId: string;
  symbol: string;
  name: string;
  exchange: string;
  sector: string | null;
  currency: string;
  notes: string | null;
  createdAt: string;
  latestQuote: WatchlistItemQuoteResponse | null;
}

export interface WatchlistResponse {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
  items: WatchlistItemResponse[];
}

export interface WatchlistCollectionResponse {
  watchlists: WatchlistResponse[];
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
}

export interface CreateWatchlistRequest {
  name: string;
  description?: string;
}

export interface UpdateWatchlistRequest {
  name: string;
  description?: string;
}

export interface AddWatchlistItemRequest {
  symbol: string;
  notes?: string;
}

export type TransactionSide = "BUY" | "SELL";
export type FeeTaxPolicy = "EXCLUDED_MVP" | "EXPLICIT_PER_TXN";
export type PortfolioValuationStatus =
  | "READY"
  | "EMPTY"
  | "STALE_DATA"
  | "PARTIAL_UNAVAILABLE";

export interface PortfolioSummaryResponse {
  id: string;
  name: string;
  description: string | null;
  baseCurrency: string;
  feeTaxPolicy: FeeTaxPolicy;
  openPositionsCount: number;
  transactionCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePortfolioRequest {
  name: string;
  description?: string;
  baseCurrency?: string;
  feeTaxPolicy?: FeeTaxPolicy;
}

export interface PortfolioTransactionResponse {
  id: string;
  instrumentId: string;
  symbol: string;
  instrumentName: string;
  side: TransactionSide;
  quantity: number;
  unitPrice: number;
  grossAmount: number;
  feeAmount: number;
  taxAmount: number;
  currency: string;
  executedAt: string;
  notes: string | null;
  createdAt: string;
}

export interface CreateTransactionRequest {
  symbol: string;
  side: TransactionSide;
  quantity: number;
  unitPrice: number;
  feeAmount?: number;
  taxAmount?: number;
  executedAt?: string;
  notes?: string;
}

export interface PortfolioHoldingResponse {
  instrumentId: string;
  symbol: string;
  name: string;
  exchange: string;
  currency: string;
  openQuantity: number;
  averageCost: number;
  costBasis: number;
  latestPrice: number | null;
  quotedAt: string | null;
  simulated: boolean;
  marketValue: number | null;
  unrealizedPnl: number | null;
  unrealizedPnlPercent: number | null;
  realizedPnl: number;
  totalFees: number;
  totalTaxes: number;
}

export interface PortfolioPerformanceResponse {
  portfolioId: string;
  name: string;
  description: string | null;
  baseCurrency: string;
  feeTaxPolicy: FeeTaxPolicy;
  feeTaxPolicyNote: string;
  valuationStatus: PortfolioValuationStatus;
  totalCostBasis: number;
  totalMarketValue: number | null;
  totalUnrealizedPnl: number | null;
  totalUnrealizedPnlPercent: number | null;
  totalRealizedPnl: number;
  totalNetPnl: number | null;
  totalFees: number;
  totalTaxes: number;
  openPositionsCount: number;
  closedPositionsCount: number;
  transactionCount: number;
  holdings: PortfolioHoldingResponse[];
  calculatedAt: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  disclaimer: string;
}

export type AlertConditionType =
  | "PRICE_ABOVE"
  | "PRICE_BELOW"
  | "INDICATOR_ABOVE"
  | "INDICATOR_BELOW";

export type AlertStatus = "ACTIVE" | "PAUSED";
export type AlertTriggerState = "ARMED" | "TRIGGERED";
export type EvaluationOutcomeType =
  | "TRIGGERED"
  | "NOT_MET"
  | "COOLDOWN_SUPPRESSED"
  | "DUPLICATE_SUPPRESSED"
  | "SKIPPED_STALE_DATA"
  | "SKIPPED_INSUFFICIENT_DATA";

export interface AlertRuleResponse {
  id: string;
  instrumentId: string;
  symbol: string;
  instrumentName: string;
  conditionType: AlertConditionType;
  indicatorType: IndicatorType | null;
  indicatorField: string | null;
  interval: string;
  thresholdValue: number;
  cooldownMinutes: number;
  status: AlertStatus;
  triggerState: AlertTriggerState;
  lastEvaluatedAt: string | null;
  lastTriggeredAt: string | null;
  lastObservedValue: number | null;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AlertRuleListResponse {
  alerts: AlertRuleResponse[];
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
}

export interface CreateAlertRequest {
  symbol: string;
  conditionType: AlertConditionType;
  indicatorType?: IndicatorType;
  indicatorField?: string;
  interval?: string;
  thresholdValue: number;
  cooldownMinutes?: number;
  note?: string;
}

export interface UpdateAlertRequest {
  status?: AlertStatus;
  thresholdValue?: number;
  cooldownMinutes?: number;
  note?: string;
}

export interface EvaluatedRuleResult {
  alertRuleId: string;
  symbol: string;
  outcome: EvaluationOutcomeType;
  observedValue: number | null;
  reason: string;
}

export interface AlertEvaluationRunResponse {
  evaluatedCount: number;
  triggeredCount: number;
  suppressedCount: number;
  evaluatedAt: string;
  dataSource: DataSourceMetadataResponse;
  results: EvaluatedRuleResult[];
}

export interface AlertNotificationResponse {
  id: string;
  alertRuleId: string;
  symbol: string;
  conditionType: AlertConditionType;
  indicatorType: IndicatorType | null;
  indicatorField: string | null;
  thresholdValue: number;
  observedValue: number;
  dataTimestamp: string;
  providerId: string;
  providerDisplayName: string;
  providerMode: DataProviderMode;
  simulated: boolean;
  freshnessStatus: FreshnessStatus;
  staleData: boolean;
  message: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPageResponse {
  items: AlertNotificationResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
}

export type AiExplanationStatus =
  | "GENERATED"
  | "FALLBACK"
  | "INSUFFICIENT_DATA";

export type AiFallbackReason =
  | "TEMPLATE_MODE_SELECTED"
  | "PROVIDER_UNCONFIGURED"
  | "PROVIDER_UNAVAILABLE"
  | "PROVIDER_TIMEOUT"
  | "MALFORMED_OUTPUT"
  | "SCHEMA_VALIDATION_FAILED"
  | "GUARDRAIL_VIOLATION"
  | "INSUFFICIENT_DATA_SHORT_CIRCUIT";

export interface StructuredAiExplanation {
  summary: string;
  observedFacts: string[];
  interpretation: string[];
  uncertaintiesAndRisks: string[];
  confidenceNote: string;
}

export interface AiExplanationResponse {
  explanationId: string;
  analysisRunId: string;
  scoreRunId: string;
  symbol: string;
  interval: string;
  adjusted: boolean;
  providerId: string;
  requestedProviderId: string;
  modelName: string;
  promptVersion: string;
  status: AiExplanationStatus;
  fallbackUsed: boolean;
  fallbackReason: AiFallbackReason | null;
  simulatedData: boolean;
  staleData: boolean;
  generatedAt: string;
  explanation: StructuredAiExplanation;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  disclaimer: string;
}

export interface AiExplanationQueryParams {
  interval?: string;
  adjusted?: boolean;
  scoreRunId?: string;
}

export type SetupCategory =
  | "HIGH_MOMENTUM_BREAKOUT"
  | "PULLBACK_WATCH"
  | "OVEREXTENDED_RISK"
  | "CONSOLIDATION"
  | "WEAK_BEARISH"
  | "INSUFFICIENT_DATA"
  | (string & {});

export interface PriceZoneResponse {
  low: number;
  high: number;
  label: string;
  rationale: string;
}

export interface BistMarginReferenceResponse {
  referencePrice: number;
  previousClose: number;
  dailyChangePercent: number;
  theoreticalCeilingPrice: number;
  theoreticalFloorPrice: number;
  distanceToCeilingPercent: number;
  distanceToFloorPercent: number;
  note: string;
}

export interface RiskRewardMetricsResponse {
  riskDistancePercent: number;
  upsideToR1Percent: number;
  upsideToR2Percent: number;
  riskRewardToR1: number;
  riskRewardToR2: number;
}

export interface OpportunityItemResponse {
  symbol: string;
  name: string;
  exchange: string;
  sector: string | null;
  currency: string;
  status: ScoreStatus;
  category: SetupCategory;
  staleData: boolean;
  simulated: boolean;
  technicalScore: number | null;
  momentumBreakoutScore: number | null;
  regime: ScoreRegime;
  trendDirection: TrendDirection;
  technicalReferencePrice: number | null;
  latestRsi: number | null;
  volumeRatio: number | null;
  atr14: number | null;
  atrPercent: number | null;
  breakoutTriggerLevel: number | null;
  distanceToBreakoutPercent: number | null;
  breakoutConfirmed: boolean;
  supportWatchZone: PriceZoneResponse | null;
  resistanceExitZone: PriceZoneResponse | null;
  invalidationRiskLevel: number | null;
  invalidationRationale: string | null;
  riskReward: RiskRewardMetricsResponse | null;
  bistMargin: BistMarginReferenceResponse | null;
  usedIndicators: string[];
  rationaleItems: string[];
  riskWarnings: string[];
  insufficientDataReasons: string[];
  headline: string;
  calculatedAt: string;
}

export interface OpportunitiesPageResponse {
  exchange: string;
  interval: string;
  adjusted: boolean;
  setupEngineVersion: string;
  scoringAlgorithmVersion: string;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  categoryCounts: Record<string, number>;
  disclaimer: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
  items: OpportunityItemResponse[];
}

export interface InstrumentSetupResponse {
  symbol: string;
  name: string;
  exchange: string;
  sector: string | null;
  currency: string;
  interval: string;
  adjusted: boolean;
  from: string;
  to: string;
  barCount: number;
  setupEngineVersion: string;
  scoringAlgorithmVersion: string;
  indicatorEngineVersion: string;
  status: ScoreStatus;
  category: SetupCategory;
  staleData: boolean;
  simulated: boolean;
  technicalScore: number | null;
  momentumBreakoutScore: number | null;
  regime: ScoreRegime;
  trendDirection: TrendDirection;
  technicalReferencePrice: number | null;
  latestRsi: number | null;
  volumeRatio: number | null;
  atr14: number | null;
  atrPercent: number | null;
  breakoutTriggerLevel: number | null;
  distanceToBreakoutPercent: number | null;
  breakoutConfirmed: boolean;
  supportWatchZone: PriceZoneResponse | null;
  resistanceExitZone: PriceZoneResponse | null;
  invalidationRiskLevel: number | null;
  invalidationRationale: string | null;
  riskReward: RiskRewardMetricsResponse | null;
  bistMargin: BistMarginReferenceResponse | null;
  usedIndicators: string[];
  rationaleItems: string[];
  riskWarnings: string[];
  insufficientDataReasons: string[];
  headline: string;
  calculatedAt: string;
  disclaimer: string;
  dataSource: DataSourceMetadataResponse;
  freshness: FreshnessMetadataResponse;
}

export type OpportunitiesSortField =
  | "momentum"
  | "score"
  | "volumeRatio"
  | "ceilingDistance"
  | "riskReward"
  | "symbol";

export interface OpportunitiesQueryParams {
  exchange?: string;
  interval?: string;
  scope?: UniverseScope;
  query?: string;
  category?: SetupCategory;
  minMomentumScore?: number;
  includeInsufficientData?: boolean;
  sortBy?: OpportunitiesSortField;
  sortDir?: "asc" | "desc";
  page?: number;
  size?: number;
}

export interface InstrumentSetupQueryParams {
  interval?: string;
  from?: string;
  to?: string;
  adjusted?: boolean;
}

export interface UniverseSyncStatusResponse {
  exchange: string;
  interval: string;
  providerId: string;
  totalActiveInstruments: number;
  syncedInstruments: number;
  remainingInstruments: number;
}

export interface BatchSyncResponse {
  exchange: string;
  interval: string;
  providerId: string;
  totalActiveInstruments: number;
  syncedInstruments: number;
  remainingInstruments: number;
  processedInBatch: number;
  succeededInBatch: number;
  failedInBatch: number;
  syncedSymbols: string[];
  failedSymbols: string[];
}
