import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClient, ApiClientError } from "./client";

describe("ApiClient", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
  });

  it("attaches bearer token and builds clean query parameters", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { "content-type": "application/json" },
      })
    );

    const client = new ApiClient("http://localhost:8080/");
    client.setTokenProvider(() => "jwt-token-123");

    const result = await client.request<{ ok: boolean }>("/api/v1/screener", {
      query: {
        exchange: "BIST",
        minScore: 55,
        empty: "",
        unset: undefined,
        nothing: null,
      },
    });

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/screener?exchange=BIST&minScore=55");
    expect((init?.headers as Record<string, string>).Authorization).toBe(
      "Bearer jwt-token-123"
    );
  });

  it("omits Authorization header when requiresAuth is false", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ status: "UP" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      })
    );

    const client = new ApiClient("http://localhost:8080");
    client.setTokenProvider(() => "should-not-be-sent");

    await client.request("/api/v1/health", { requiresAuth: false });

    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("parses structured ApiErrorResponse and notifies unauthorized handler on 401", async () => {
    const onUnauthorized = vi.fn();
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          timestamp: "2026-11-19T10:00:00Z",
          status: 401,
          code: "UNAUTHORIZED",
          message: "JWT token expired",
          path: "/api/v1/users/me",
          traceId: "trace-abc-99",
          fieldErrors: [{ field: "token", message: "Expired" }],
        }),
        {
          status: 401,
          headers: { "content-type": "application/json" },
        }
      )
    );

    const client = new ApiClient("http://localhost:8080");
    client.setTokenProvider(() => "expired-token");
    client.setUnauthorizedHandler(onUnauthorized);

    await expect(client.request("/api/v1/users/me")).rejects.toSatisfy(
      (err: unknown) =>
        err instanceof ApiClientError &&
        err.status === 401 &&
        err.code === "UNAUTHORIZED" &&
        err.traceId === "trace-abc-99" &&
        err.fieldErrors?.length === 1
    );
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("throws structured NETWORK_ERROR when fetch rejects", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const client = new ApiClient("http://localhost:8080");

    await expect(client.request("/api/v1/health", { requiresAuth: false })).rejects.toSatisfy(
      (err: unknown) =>
        err instanceof ApiClientError &&
        err.status === 0 &&
        err.code === "NETWORK_ERROR"
    );
  });
});
