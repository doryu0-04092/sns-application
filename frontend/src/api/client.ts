const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export class ApiError extends Error {
  code: string;
  status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

interface ErrorEnvelope {
  error: { code: string; message: string };
}

interface DataEnvelope<T> {
  data: T;
}

const NO_REFRESH_PATHS = new Set(["/auth/login", "/auth/signup", "/auth/refresh"]);

let refreshPromise: Promise<boolean> | null = null;

function rawFetch(path: string, init?: RequestInit): Promise<Response> {
  const isFormData = init?.body instanceof FormData;
  return fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      // FormData送信時はブラウザがboundary付きのmultipart/form-dataヘッダーを自動生成するため、
      // ここで固定のContent-Typeを設定してはいけない。
      ...(isFormData ? {} : { "Content-Type": "application/json" }),
      ...init?.headers,
    },
  });
}

/**
 * アクセストークン(15分)の失効による401を、リフレッシュトークンでの
 * サイレントな再認証+ 元のリクエストの1回だけの再試行で吸収する。
 * 同時に複数のリクエストが401になっても、進行中のリフレッシュ処理を
 * 共有し、二重にリフレッシュを走らせない。
 */
function tryRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = rawFetch("/auth/refresh", { method: "POST" })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await rawFetch(path, init);

  if (res.status === 401 && !NO_REFRESH_PATHS.has(path)) {
    const refreshed = await tryRefresh();
    if (refreshed) {
      res = await rawFetch(path, init);
    }
  }

  const body = (await res.json()) as DataEnvelope<T> | ErrorEnvelope;

  if (!res.ok) {
    const { code, message } = (body as ErrorEnvelope).error;
    throw new ApiError(code, message, res.status);
  }

  return (body as DataEnvelope<T>).data;
}
