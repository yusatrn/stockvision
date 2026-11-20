import type { ApiErrorResponse, ApiFieldError } from "./types";

const RAW_API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "").trim();

export const DEFAULT_API_BASE_URL =
  !RAW_API_BASE_URL ||
  /^https?:\/\/(localhost|127\.0\.0\.1):8080\/?$/i.test(RAW_API_BASE_URL)
    ? ""
    : RAW_API_BASE_URL;

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;
  readonly path?: string;
  readonly fieldErrors?: ApiFieldError[];

  constructor(params: {
    message: string;
    status: number;
    code: string;
    traceId?: string;
    path?: string;
    fieldErrors?: ApiFieldError[];
  }) {
    super(params.message);
    this.name = "ApiClientError";
    this.status = params.status;
    this.code = params.code;
    this.traceId = params.traceId;
    this.path = params.path;
    this.fieldErrors = params.fieldErrors;
  }
}

export function isApiClientError(err: unknown): err is ApiClientError {
  return err instanceof ApiClientError;
}

export type TokenProvider = () => string | null;
export type UnauthorizedHandler = () => void;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  requiresAuth?: boolean;
  signal?: AbortSignal;
}

function buildQueryString(
  query?: Record<string, string | number | boolean | undefined | null>
): string {
  if (!query) {
    return "";
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") {
      continue;
    }
    params.set(key, String(value));
  }
  const encoded = params.toString();
  return encoded ? `?${encoded}` : "";
}

export class ApiClient {
  private readonly baseUrl: string;
  private tokenProvider: TokenProvider = () => null;
  private unauthorizedHandler?: UnauthorizedHandler;

  constructor(baseUrl: string = DEFAULT_API_BASE_URL) {
    this.baseUrl = baseUrl.replace(/\/+$/, "");
  }

  setTokenProvider(provider: TokenProvider): void {
    this.tokenProvider = provider;
  }

  setUnauthorizedHandler(handler: UnauthorizedHandler | undefined): void {
    this.unauthorizedHandler = handler;
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const {
      method = "GET",
      query,
      body,
      requiresAuth = true,
      signal,
    } = options;

    const normalizedPath = path.startsWith("/") ? path : `/${path}`;
    const url = `${this.baseUrl}${normalizedPath}${buildQueryString(query)}`;
    const headers: Record<string, string> = {
      Accept: "application/json",
    };

    const token = requiresAuth ? this.tokenProvider() : null;
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    let serializedBody: string | undefined;
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
      serializedBody = JSON.stringify(body);
    }

    let response: Response;
    try {
      response = await fetch(url, {
        method,
        headers,
        body: serializedBody,
        signal,
      });
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw error;
      }
      throw new ApiClientError({
        status: 0,
        code: "NETWORK_ERROR",
        message:
          "StockVision AI sunucu API'sine ulaşılamıyor. API sunucusunun çalıştığından ve CORS ayarlarının bu kaynağı kabul ettiğinden emin olun.",
      });
    }

    const contentType = response.headers.get("content-type") ?? "";
    const hasJson = contentType.includes("application/json");
    let parsedBody: unknown = null;

    if (response.status !== 204) {
      try {
        parsedBody = hasJson ? await response.json() : await response.text();
      } catch {
        parsedBody = null;
      }
    }

    if (!response.ok) {
      if (response.status === 401 && requiresAuth && this.unauthorizedHandler) {
        this.unauthorizedHandler();
      }

      if (
        parsedBody &&
        typeof parsedBody === "object" &&
        "message" in parsedBody
      ) {
        const apiErr = parsedBody as Partial<ApiErrorResponse>;
        throw new ApiClientError({
          status: apiErr.status ?? response.status,
          code: apiErr.code ?? `HTTP_${response.status}`,
          message:
            typeof apiErr.message === "string" && apiErr.message.length > 0
              ? apiErr.message
              : response.statusText || "İstek başarısız oldu",
          traceId: apiErr.traceId,
          path: apiErr.path ?? normalizedPath,
          fieldErrors: apiErr.fieldErrors,
        });
      }

      throw new ApiClientError({
        status: response.status,
        code: `HTTP_${response.status}`,
        message:
          typeof parsedBody === "string" && parsedBody.trim().length > 0
            ? parsedBody
            : response.statusText || "Beklenmeyen sunucu hatası",
        path: normalizedPath,
      });
    }

    return parsedBody as T;
  }
}

export const apiClient = new ApiClient();
