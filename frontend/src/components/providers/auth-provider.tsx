"use client";

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { apiClient, isApiClientError } from "@/lib/api/client";
import { apiServices } from "@/lib/api/services";
import type {
  RegisterRequest,
  RegisterResponse,
  UserProfileResponse,
} from "@/lib/api/types";
import { DEFAULT_TIMEZONE } from "@/lib/format";

const TOKEN_STORAGE_KEY = "stockvision.auth.token";
const REFRESH_TOKEN_STORAGE_KEY = "stockvision.auth.refreshToken";
const EXPIRES_AT_STORAGE_KEY = "stockvision.auth.expiresAt";
const TIMEZONE_STORAGE_KEY = "stockvision.display.timezone";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  user: UserProfileResponse | null;
  token: string | null;
  effectiveTimezone: string;
  sessionNotice: string | null;
  login: (email: string, password: string) => Promise<UserProfileResponse>;
  register: (payload: RegisterRequest) => Promise<RegisterResponse>;
  logout: () => void;
  refreshProfile: () => Promise<void>;
  setDisplayTimezone: (timezone: string) => void;
  clearSessionNotice: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function clearStoredAuth() {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    window.localStorage.removeItem(EXPIRES_AT_STORAGE_KEY);
  } catch {
    // Ignore storage restriction.
  }
}

function storeAuthTokens(
  accessToken: string,
  expiresAt: string,
  refreshToken?: string
) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, accessToken);
    window.localStorage.setItem(EXPIRES_AT_STORAGE_KEY, expiresAt);
    if (refreshToken) {
      window.localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken);
    }
  } catch {
    // Ignore storage failures in restricted contexts.
  }
}

function isExpiredIso(expiresAt: string | null): boolean {
  if (!expiresAt) {
    return false;
  }
  const ts = Date.parse(expiresAt);
  if (Number.isNaN(ts)) {
    return false;
  }
  return ts <= Date.now();
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<UserProfileResponse | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [timezoneOverride, setTimezoneOverride] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  const tokenRef = useRef<string | null>(null);
  tokenRef.current = token;

  const handleUnauthorized = useCallback(() => {
    tokenRef.current = null;
    clearStoredAuth();
    setToken(null);
    setUser(null);
    setStatus("unauthenticated");
    setSessionNotice("Oturumunuzun süresi doldu veya geçersiz. Lütfen tekrar giriş yapın.");
  }, []);

  useEffect(() => {
    apiClient.setTokenProvider(() => tokenRef.current);
    apiClient.setUnauthorizedHandler(handleUnauthorized);
    return () => {
      apiClient.setUnauthorizedHandler(undefined);
    };
  }, [handleUnauthorized]);

  useEffect(() => {
    let active = true;

    async function hydrateSession() {
      try {
        const storedTz = window.localStorage.getItem(TIMEZONE_STORAGE_KEY);
        if (storedTz && active) {
          setTimezoneOverride(storedTz);
        }

        const storedToken = window.localStorage.getItem(TOKEN_STORAGE_KEY);
        const storedRefreshToken = window.localStorage.getItem(
          REFRESH_TOKEN_STORAGE_KEY
        );
        const storedExpires = window.localStorage.getItem(EXPIRES_AT_STORAGE_KEY);

        if (!storedToken || isExpiredIso(storedExpires)) {
          if (storedRefreshToken) {
            try {
              const refreshed = await apiServices.auth.refresh(storedRefreshToken);
              if (!active) {
                return;
              }
              tokenRef.current = refreshed.accessToken;
              setToken(refreshed.accessToken);
              setUser(refreshed.user);
              setStatus("authenticated");
              storeAuthTokens(
                refreshed.accessToken,
                refreshed.expiresAt,
                refreshed.refreshToken
              );
              return;
            } catch {
              // Refresh failed or revoked; clear stored credentials below.
            }
          }
          clearStoredAuth();
          if (active) {
            setToken(null);
            tokenRef.current = null;
            setUser(null);
            setStatus("unauthenticated");
          }
          return;
        }

        tokenRef.current = storedToken;
        setToken(storedToken);

        const profile = await apiServices.users.getMe();
        if (active) {
          setUser(profile);
          setStatus("authenticated");
        }
      } catch (err) {
        if (!active) {
          return;
        }
        if (isApiClientError(err) && err.status === 401) {
          clearStoredAuth();
          tokenRef.current = null;
          setToken(null);
          setUser(null);
          setStatus("unauthenticated");
          return;
        }
        // Keep token on transient network error? Clear to unauthenticated only if no profile exists so user can retry or re-login cleanly.
        clearStoredAuth();
        tokenRef.current = null;
        setToken(null);
        setUser(null);
        setStatus("unauthenticated");
      }
    }

    hydrateSession();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setSessionNotice(null);
    const response = await apiServices.auth.login({ email, password });
    tokenRef.current = response.accessToken;
    setToken(response.accessToken);
    setUser(response.user);
    setStatus("authenticated");

    storeAuthTokens(
      response.accessToken,
      response.expiresAt,
      response.refreshToken
    );

    return response.user;
  }, []);

  const register = useCallback(async (payload: RegisterRequest) => {
    return apiServices.auth.register(payload);
  }, []);

  const logout = useCallback(() => {
    let storedRefreshToken: string | null = null;
    if (typeof window !== "undefined") {
      try {
        storedRefreshToken = window.localStorage.getItem(
          REFRESH_TOKEN_STORAGE_KEY
        );
      } catch {
        storedRefreshToken = null;
      }
    }
    if (storedRefreshToken) {
      apiServices.auth.logout(storedRefreshToken).catch(() => undefined);
    }
    tokenRef.current = null;
    clearStoredAuth();
    setToken(null);
    setUser(null);
    setSessionNotice(null);
    setStatus("unauthenticated");
  }, []);

  const refreshProfile = useCallback(async () => {
    if (!tokenRef.current) {
      return;
    }
    const profile = await apiServices.users.getMe();
    setUser(profile);
  }, []);

  const setDisplayTimezone = useCallback((timezone: string) => {
    const trimmed = timezone.trim();
    if (!trimmed) {
      setTimezoneOverride(null);
      try {
        window.localStorage.removeItem(TIMEZONE_STORAGE_KEY);
      } catch {
        // Ignore storage error.
      }
      return;
    }
    setTimezoneOverride(trimmed);
    try {
      window.localStorage.setItem(TIMEZONE_STORAGE_KEY, trimmed);
    } catch {
      // Ignore storage error.
    }
  }, []);

  const clearSessionNotice = useCallback(() => {
    setSessionNotice(null);
  }, []);

  const effectiveTimezone = useMemo(
    () => timezoneOverride || user?.timezone || DEFAULT_TIMEZONE,
    [timezoneOverride, user?.timezone]
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      token,
      effectiveTimezone,
      sessionNotice,
      login,
      register,
      logout,
      refreshProfile,
      setDisplayTimezone,
      clearSessionNotice,
    }),
    [
      status,
      user,
      token,
      effectiveTimezone,
      sessionNotice,
      login,
      register,
      logout,
      refreshProfile,
      setDisplayTimezone,
      clearSessionNotice,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return ctx;
}
