import type { HttpRequest, LiveEvent, Platform } from "./ports";

export type BrowserSession = {
  apiUrl: string;
  token: string;
  expiresAt?: string;
};

export type SessionProvider = () => Promise<BrowserSession>;

type BrowserPlatformOptions = {
  clientPlatform?: string;
  defaultTimeoutMs?: number;
};

const EXPIRY_MARGIN_MS = 30_000;
const RECONNECT_MIN_MS = 1_000;
const RECONNECT_MAX_MS = 30_000;
const SOCKET_PATH = "/v1/me/events/socket";

function isFresh(session: BrowserSession | null): session is BrowserSession {
  if (!session) return false;
  if (!session.expiresAt) return true;
  return Date.parse(session.expiresAt) - Date.now() > EXPIRY_MARGIN_MS;
}

export function createBrowserPlatform(
  getSession: SessionProvider,
  options: BrowserPlatformOptions = {},
): Platform {
  const { clientPlatform = "webview", defaultTimeoutMs = 8_000 } = options;
  let cached: BrowserSession | null = null;
  let pending: Promise<BrowserSession> | null = null;

  const session = async (force = false): Promise<BrowserSession> => {
    if (!force && isFresh(cached)) return cached;
    pending ??= getSession()
      .then((next) => {
        cached = next;
        return next;
      })
      .finally(() => {
        pending = null;
      });
    return pending;
  };

  const send = async <T>(req: HttpRequest, force: boolean): Promise<T> => {
    const current = await session(force);
    const url = new URL(req.path, current.apiUrl);
    for (const [key, value] of Object.entries(req.query ?? {})) {
      url.searchParams.set(key, value);
    }
    const controller = new AbortController();
    const timer = setTimeout(
      () => controller.abort(),
      req.timeoutMs ?? defaultTimeoutMs,
    );
    try {
      const headers: Record<string, string> = {
        authorization: `Bearer ${current.token}`,
      };
      if (req.body !== undefined) headers["content-type"] = "application/json";
      const resp = await fetch(url, {
        method: req.method,
        headers,
        body: req.body === undefined ? undefined : JSON.stringify(req.body),
        signal: controller.signal,
      });
      if (resp.status === 401 && !force) return send<T>(req, true);
      if (!resp.ok) throw new Error(`${req.path} failed: ${resp.status}`);
      if (resp.status === 204) return undefined as T;
      return (await resp.json()) as T;
    } finally {
      clearTimeout(timer);
    }
  };

  const handlers = new Set<(event: LiveEvent) => void>();
  let socket: WebSocket | null = null;
  let retryTimer: ReturnType<typeof setTimeout> | undefined;
  let attempt = 0;
  let hasConnected = false;

  const connect = async () => {
    if (handlers.size === 0) return;
    try {
      const current = await session();
      const url = new URL(SOCKET_PATH, current.apiUrl);
      url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
      url.searchParams.set("platform", clientPlatform);
      const ws = new WebSocket(url, ["vox.v1", `bearer.${current.token}`]);
      socket = ws;
      ws.onopen = () => {
        attempt = 0;
        if (hasConnected) {
          handlers.forEach((handler) => handler({ type: "live_reconnected" }));
        }
        hasConnected = true;
      };
      ws.onmessage = (message) => {
        try {
          const parsed = JSON.parse(String(message.data)) as LiveEvent;
          if (typeof parsed.type !== "string") return;
          handlers.forEach((handler) => handler(parsed));
        } catch {
          return;
        }
      };
      ws.onclose = () => {
        if (socket === ws) socket = null;
        schedule();
      };
    } catch {
      schedule();
    }
  };

  const schedule = () => {
    if (handlers.size === 0 || retryTimer) return;
    const delay = Math.min(RECONNECT_MIN_MS * 2 ** attempt, RECONNECT_MAX_MS);
    attempt += 1;
    retryTimer = setTimeout(() => {
      retryTimer = undefined;
      void connect();
    }, delay);
  };

  return {
    http: { request: <T>(req: HttpRequest) => send<T>(req, false) },
    live: {
      subscribe: (handler) => {
        handlers.add(handler);
        if (handlers.size === 1 && !socket) void connect();
        return () => {
          handlers.delete(handler);
          if (handlers.size === 0) {
            clearTimeout(retryTimer);
            retryTimer = undefined;
            const closing = socket;
            socket = null;
            closing?.close();
          }
        };
      },
    },
  };
}
