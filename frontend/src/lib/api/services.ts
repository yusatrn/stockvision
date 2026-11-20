import { apiClient, type ApiClient } from "./client";
import type {
  AddWatchlistItemRequest,
  AiExplanationQueryParams,
  AiExplanationResponse,
  AlertEvaluationRunResponse,
  AlertNotificationResponse,
  AlertRuleListResponse,
  AlertRuleResponse,
  AnalysisQueryParams,
  AuthTokenResponse,
  BatchSyncResponse,
  CreateAlertRequest,
  CreatePortfolioRequest,
  CreateTransactionRequest,
  CreateWatchlistRequest,
  ExchangeResponse,
  HealthResponse,
  HistoricalBarsParams,
  HistoricalBarsResponse,
  InstrumentAnalysisResponse,
  InstrumentDetailResponse,
  InstrumentListParams,
  InstrumentPageResponse,
  InstrumentSetupQueryParams,
  InstrumentSetupResponse,
  LoginRequest,
  MarketSummaryResponse,
  NotificationPageResponse,
  OpportunitiesPageResponse,
  OpportunitiesQueryParams,
  PortfolioPerformanceResponse,
  PortfolioSummaryResponse,
  PortfolioTransactionResponse,
  RegisterRequest,
  RegisterResponse,
  ScreenerPageResponse,
  ScreenerQueryParams,
  TechnicalIndicatorsParams,
  TechnicalIndicatorsResponse,
  UniverseSyncStatusResponse,
  UpdateAlertRequest,
  UpdateWatchlistRequest,
  UserProfileResponse,
  WatchlistCollectionResponse,
  WatchlistItemResponse,
  WatchlistResponse,
} from "./types";

export function createApiServices(client: ApiClient = apiClient) {
  return {
    health: {
      getHealth(signal?: AbortSignal) {
        return client.request<HealthResponse>("/api/v1/health", {
          requiresAuth: false,
          signal,
        });
      },
    },
    auth: {
      login(payload: LoginRequest, signal?: AbortSignal) {
        return client.request<AuthTokenResponse>("/api/v1/auth/login", {
          method: "POST",
          body: payload,
          requiresAuth: false,
          signal,
        });
      },
      register(payload: RegisterRequest, signal?: AbortSignal) {
        return client.request<RegisterResponse>("/api/v1/auth/register", {
          method: "POST",
          body: payload,
          requiresAuth: false,
          signal,
        });
      },
      refresh(refreshToken: string, signal?: AbortSignal) {
        return client.request<AuthTokenResponse>("/api/v1/auth/refresh", {
          method: "POST",
          body: { refreshToken },
          requiresAuth: false,
          signal,
        });
      },
      logout(refreshToken?: string | null, signal?: AbortSignal) {
        return client.request<void>("/api/v1/auth/logout", {
          method: "POST",
          body: refreshToken ? { refreshToken } : {},
          requiresAuth: false,
          signal,
        });
      },
    },
    users: {
      getMe(signal?: AbortSignal) {
        return client.request<UserProfileResponse>("/api/v1/users/me", {
          signal,
        });
      },
    },
    markets: {
      listMarkets(signal?: AbortSignal) {
        return client.request<ExchangeResponse[]>("/api/v1/markets", {
          signal,
        });
      },
      getSummary(exchange?: string, signal?: AbortSignal) {
        return client.request<MarketSummaryResponse>("/api/v1/markets/summary", {
          query: { exchange },
          signal,
        });
      },
    },
    instruments: {
      list(params: InstrumentListParams = {}, signal?: AbortSignal) {
        return client.request<InstrumentPageResponse>("/api/v1/instruments", {
          query: {
            query: params.query,
            exchange: params.exchange,
            page: params.page,
            size: params.size,
          },
          signal,
        });
      },
      getDetail(symbol: string, signal?: AbortSignal) {
        return client.request<InstrumentDetailResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}`,
          { signal }
        );
      },
      getBars(
        symbol: string,
        params: HistoricalBarsParams = {},
        signal?: AbortSignal
      ) {
        return client.request<HistoricalBarsResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}/bars`,
          {
            query: {
              interval: params.interval,
              from: params.from,
              to: params.to,
              adjusted: params.adjusted,
            },
            signal,
          }
        );
      },
      getIndicators(
        symbol: string,
        params: TechnicalIndicatorsParams = {},
        signal?: AbortSignal
      ) {
        return client.request<TechnicalIndicatorsResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}/indicators`,
          {
            query: {
              interval: params.interval,
              indicators:
                params.indicators && params.indicators.length > 0
                  ? params.indicators.join(",")
                  : undefined,
              from: params.from,
              to: params.to,
              adjusted: params.adjusted,
            },
            signal,
          }
        );
      },
      getAnalysis(
        symbol: string,
        params: AnalysisQueryParams = {},
        signal?: AbortSignal
      ) {
        return client.request<InstrumentAnalysisResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}/analysis`,
          {
            query: {
              interval: params.interval,
              from: params.from,
              to: params.to,
              adjusted: params.adjusted,
            },
            signal,
          }
        );
      },
      getSetup(
        symbol: string,
        params: InstrumentSetupQueryParams = {},
        signal?: AbortSignal
      ) {
        return client.request<InstrumentSetupResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}/setups`,
          {
            query: {
              interval: params.interval,
              from: params.from,
              to: params.to,
              adjusted: params.adjusted,
            },
            signal,
          }
        );
      },
      getAiExplanation(
        symbol: string,
        params: AiExplanationQueryParams = {},
        signal?: AbortSignal
      ) {
        return client.request<AiExplanationResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}/ai-explanation`,
          {
            query: {
              interval: params.interval,
              adjusted: params.adjusted,
              scoreRunId: params.scoreRunId,
            },
            signal,
          }
        );
      },
      regenerateAiExplanation(
        symbol: string,
        params: AiExplanationQueryParams = {},
        signal?: AbortSignal
      ) {
        return client.request<AiExplanationResponse>(
          `/api/v1/instruments/${encodeURIComponent(symbol)}/ai-explanation/regenerate`,
          {
            method: "POST",
            query: {
              interval: params.interval,
              adjusted: params.adjusted,
              scoreRunId: params.scoreRunId,
            },
            signal,
          }
        );
      },
    },
    screener: {
      screen(params: ScreenerQueryParams = {}, signal?: AbortSignal) {
        return client.request<ScreenerPageResponse>("/api/v1/screener", {
          query: {
            exchange: params.exchange,
            interval: params.interval,
            scope: params.scope,
            query: params.query,
            minScore: params.minScore,
            maxScore: params.maxScore,
            rsiMin: params.rsiMin,
            rsiMax: params.rsiMax,
            trend: params.trend,
            includeInsufficientData: params.includeInsufficientData,
            sortBy: params.sortBy,
            sortDir: params.sortDir,
            page: params.page,
            size: params.size,
          },
          signal,
        });
      },
    },
    opportunities: {
      list(params: OpportunitiesQueryParams = {}, signal?: AbortSignal) {
        return client.request<OpportunitiesPageResponse>("/api/v1/opportunities", {
          query: {
            exchange: params.exchange,
            interval: params.interval,
            scope: params.scope,
            query: params.query,
            category: params.category,
            minMomentumScore: params.minMomentumScore,
            includeInsufficientData: params.includeInsufficientData,
            sortBy: params.sortBy,
            sortDir: params.sortDir,
            page: params.page,
            size: params.size,
          },
          signal,
        });
      },
    },
    marketData: {
      getSyncStatus(
        params: { exchange?: string; interval?: string } = {},
        signal?: AbortSignal
      ) {
        return client.request<UniverseSyncStatusResponse>(
          "/api/v1/market-data/sync-status",
          {
            query: {
              exchange: params.exchange,
              interval: params.interval,
            },
            signal,
          }
        );
      },
      syncBatch(
        params: { exchange?: string; interval?: string; batchSize?: number } = {},
        signal?: AbortSignal
      ) {
        return client.request<BatchSyncResponse>(
          "/api/v1/market-data/sync-batch",
          {
            method: "POST",
            query: {
              exchange: params.exchange,
              interval: params.interval,
              batchSize: params.batchSize,
            },
            signal,
          }
        );
      },
    },
    watchlists: {
      list(signal?: AbortSignal) {
        return client.request<WatchlistCollectionResponse>("/api/v1/watchlists", {
          signal,
        });
      },
      create(payload: CreateWatchlistRequest, signal?: AbortSignal) {
        return client.request<WatchlistResponse>("/api/v1/watchlists", {
          method: "POST",
          body: payload,
          signal,
        });
      },
      update(id: string, payload: UpdateWatchlistRequest, signal?: AbortSignal) {
        return client.request<WatchlistResponse>(
          `/api/v1/watchlists/${encodeURIComponent(id)}`,
          {
            method: "PATCH",
            body: payload,
            signal,
          }
        );
      },
      remove(id: string, signal?: AbortSignal) {
        return client.request<void>(
          `/api/v1/watchlists/${encodeURIComponent(id)}`,
          {
            method: "DELETE",
            signal,
          }
        );
      },
      addItem(
        watchlistId: string,
        payload: AddWatchlistItemRequest,
        signal?: AbortSignal
      ) {
        return client.request<WatchlistItemResponse>(
          `/api/v1/watchlists/${encodeURIComponent(watchlistId)}/items`,
          {
            method: "POST",
            body: payload,
            signal,
          }
        );
      },
      removeItem(watchlistId: string, itemId: string, signal?: AbortSignal) {
        return client.request<void>(
          `/api/v1/watchlists/${encodeURIComponent(watchlistId)}/items/${encodeURIComponent(itemId)}`,
          {
            method: "DELETE",
            signal,
          }
        );
      },
    },
    portfolios: {
      list(signal?: AbortSignal) {
        return client.request<PortfolioSummaryResponse[]>("/api/v1/portfolios", {
          signal,
        });
      },
      create(payload: CreatePortfolioRequest, signal?: AbortSignal) {
        return client.request<PortfolioSummaryResponse>("/api/v1/portfolios", {
          method: "POST",
          body: payload,
          signal,
        });
      },
      remove(id: string, signal?: AbortSignal) {
        return client.request<void>(
          `/api/v1/portfolios/${encodeURIComponent(id)}`,
          {
            method: "DELETE",
            signal,
          }
        );
      },
      listTransactions(portfolioId: string, signal?: AbortSignal) {
        return client.request<PortfolioTransactionResponse[]>(
          `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/transactions`,
          { signal }
        );
      },
      addTransaction(
        portfolioId: string,
        payload: CreateTransactionRequest,
        signal?: AbortSignal
      ) {
        return client.request<PortfolioTransactionResponse>(
          `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/transactions`,
          {
            method: "POST",
            body: payload,
            signal,
          }
        );
      },
      removeTransaction(
        portfolioId: string,
        transactionId: string,
        signal?: AbortSignal
      ) {
        return client.request<void>(
          `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/transactions/${encodeURIComponent(transactionId)}`,
          {
            method: "DELETE",
            signal,
          }
        );
      },
      getPerformance(portfolioId: string, signal?: AbortSignal) {
        return client.request<PortfolioPerformanceResponse>(
          `/api/v1/portfolios/${encodeURIComponent(portfolioId)}/performance`,
          { signal }
        );
      },
    },
    alerts: {
      list(signal?: AbortSignal) {
        return client.request<AlertRuleListResponse>("/api/v1/alerts", {
          signal,
        });
      },
      create(payload: CreateAlertRequest, signal?: AbortSignal) {
        return client.request<AlertRuleResponse>("/api/v1/alerts", {
          method: "POST",
          body: payload,
          signal,
        });
      },
      update(id: string, payload: UpdateAlertRequest, signal?: AbortSignal) {
        return client.request<AlertRuleResponse>(
          `/api/v1/alerts/${encodeURIComponent(id)}`,
          {
            method: "PATCH",
            body: payload,
            signal,
          }
        );
      },
      remove(id: string, signal?: AbortSignal) {
        return client.request<void>(
          `/api/v1/alerts/${encodeURIComponent(id)}`,
          {
            method: "DELETE",
            signal,
          }
        );
      },
      evaluateNow(signal?: AbortSignal) {
        return client.request<AlertEvaluationRunResponse>(
          "/api/v1/alerts/evaluate",
          {
            method: "POST",
            signal,
          }
        );
      },
    },
    notifications: {
      list(
        params: { unreadOnly?: boolean; page?: number; size?: number } = {},
        signal?: AbortSignal
      ) {
        return client.request<NotificationPageResponse>("/api/v1/notifications", {
          query: {
            unreadOnly: params.unreadOnly,
            page: params.page,
            size: params.size,
          },
          signal,
        });
      },
      markRead(id: string, signal?: AbortSignal) {
        return client.request<AlertNotificationResponse>(
          `/api/v1/notifications/${encodeURIComponent(id)}/read`,
          {
            method: "PATCH",
            signal,
          }
        );
      },
      markAllRead(signal?: AbortSignal) {
        return client.request<{ updatedCount: number }>(
          "/api/v1/notifications/read-all",
          {
            method: "POST",
            signal,
          }
        );
      },
    },
  };
}

export const apiServices = createApiServices(apiClient);
