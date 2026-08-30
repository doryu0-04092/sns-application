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

/**
 * リフレッシュが成功した回数。
 *
 * <b>「自分が送ったあとにリフレッシュが終わったか」を判定するために使う。</b>
 * 進行中のリフレッシュを共有するだけでは、次の場合に二重に走る。
 *
 * <pre>
 *   A: 送信 → 401 → リフレッシュ開始 → 完了 → 再試行
 *   B: 送信(古いトークン) ────────────── → 401 → **もう1回リフレッシュ**
 * </pre>
 *
 * B が401で戻った時点で進行中のリフレッシュは無いため共有できない。
 * だが新しいトークンは既にあるので、<b>リフレッシュではなく再試行だけでよい。</b>
 *
 * 余計なリフレッシュは往復が増えるだけでなく、<b>リフレッシュトークンの回転が
 * 余分に起きる</b>。回転が増えるほど、盗用検知の猶予時間に触れる機会も増える。
 */
let refreshGeneration = 0;

function rawFetch(path: string, init?: RequestInit): Promise<Response> {
  return fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
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
      .then((res) => {
        if (res.ok) refreshGeneration += 1;
        return res.ok;
      })
      .catch(() => false)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  // 送る前の世代を控える。戻ってきたときに進んでいれば、
  // その間にリフレッシュが終わっている。
  const sentAtGeneration = refreshGeneration;

  let res = await rawFetch(path, init);

  if (res.status === 401 && !NO_REFRESH_PATHS.has(path)) {
    // **待っている間にリフレッシュが終わっていれば、もう一度は要らない。**
    // 新しいトークンは既にあるので、再試行するだけでよい。
    const refreshed =
      refreshGeneration !== sentAtGeneration ? true : await tryRefresh();
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
