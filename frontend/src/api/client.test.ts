import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "./client";

/**
 * 401時のサイレントな再認証+リトライのテスト。
 *
 * アクセストークンは15分で切れるため、この経路は通常運用で日常的に通る。
 * にもかかわらず失敗しても画面上は「ログイン画面に飛ばされる」としか見えず、
 * 「リフレッシュが多重に走る」「リトライが無限ループする」といった壊れ方は
 * 手動確認ではまず気づけない。
 */

type FetchArgs = [input: RequestInfo | URL, init?: RequestInit];

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

function ok<T>(data: T) {
  return jsonResponse(200, { data });
}

function unauthorized() {
  return jsonResponse(401, { error: { code: "UNAUTHENTICATED", message: "認証が必要です" } });
}

function pathOf(call: FetchArgs): string {
  return String(call[0]);
}

describe("apiFetch", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("成功時はdataエンベロープの中身を返す", async () => {
    fetchMock.mockResolvedValueOnce(ok({ id: 1 }));

    await expect(apiFetch("/posts")).resolves.toEqual({ id: 1 });
  });

  it("クッキーを送るためcredentials:includeを指定する", async () => {
    fetchMock.mockResolvedValueOnce(ok(null));

    await apiFetch("/posts");

    expect(fetchMock.mock.calls[0][1]).toMatchObject({ credentials: "include" });
  });

  it("FormData送信時はContent-Typeを設定しない", async () => {
    fetchMock.mockResolvedValueOnce(ok(null));

    await apiFetch("/posts", { method: "POST", body: new FormData() });

    const headers = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers["Content-Type"]).toBeUndefined();
  });

  it("エラー時はcodeとstatusを持つApiErrorを投げる", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(404, { error: { code: "POST_NOT_FOUND", message: "見つかりません" } }));

    const error = await apiFetch("/posts/1").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ code: "POST_NOT_FOUND", status: 404, message: "見つかりません" });
  });

  it("401ならリフレッシュしてから元のリクエストを1度だけ再試行する", async () => {
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok(null)) // /auth/refresh
      .mockResolvedValueOnce(ok({ id: 1 }));

    await expect(apiFetch("/posts")).resolves.toEqual({ id: 1 });

    const paths = fetchMock.mock.calls.map((c: FetchArgs) => pathOf(c));
    expect(paths[1]).toContain("/auth/refresh");
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("リフレッシュが失敗したら再試行せず401のまま返す", async () => {
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(jsonResponse(401, { error: { code: "INVALID_REFRESH_TOKEN", message: "無効です" } }));

    await expect(apiFetch("/posts")).rejects.toMatchObject({ status: 401 });
    // 元リクエスト + リフレッシュのみ。リトライは行わない
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("再試行後も401ならリフレッシュを繰り返さない", async () => {
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok(null)) // refresh成功
      .mockResolvedValueOnce(unauthorized()); // 再試行も401

    await expect(apiFetch("/posts")).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("ログイン・サインアップ・リフレッシュの401ではリフレッシュを試みない", async () => {
    for (const path of ["/auth/login", "/auth/signup", "/auth/refresh"]) {
      fetchMock.mockClear();
      fetchMock.mockResolvedValueOnce(unauthorized());

      await expect(apiFetch(path)).rejects.toMatchObject({ status: 401 });
      expect(fetchMock).toHaveBeenCalledTimes(1);
    }
  });

  /** 画面表示直後に複数のクエリが同時に401になるのは日常的に起きる。 */
  it("同時に複数のリクエストが401になってもリフレッシュは1度しか走らない", async () => {
    let resolveRefresh: (res: Response) => void = () => {};
    const refreshPending = new Promise<Response>((resolve) => {
      resolveRefresh = resolve;
    });

    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const path = String(input);
      if (path.includes("/auth/refresh")) return refreshPending;
      // 1回目は401、リトライ時は成功を返す
      return Promise.resolve(fetchMock.mock.calls.filter((c: FetchArgs) => pathOf(c) === path).length > 1
        ? ok({ path })
        : unauthorized());
    });

    const inFlight = Promise.all([apiFetch("/posts"), apiFetch("/comments"), apiFetch("/users")]);
    await Promise.resolve();
    resolveRefresh(ok(null));
    await inFlight;

    const refreshCalls = fetchMock.mock.calls.filter((c: FetchArgs) => pathOf(c).includes("/auth/refresh"));
    expect(refreshCalls).toHaveLength(1);
  });

  it("リフレッシュ完了後の新たな401では改めてリフレッシュできる", async () => {
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok(null))
      .mockResolvedValueOnce(ok({ first: true }));
    await apiFetch("/posts");

    fetchMock.mockClear();
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok(null))
      .mockResolvedValueOnce(ok({ second: true }));

    await expect(apiFetch("/posts")).resolves.toEqual({ second: true });
    expect(fetchMock.mock.calls.filter((c: FetchArgs) => pathOf(c).includes("/auth/refresh"))).toHaveLength(1);
  });

  it("リフレッシュ自体が通信エラーになっても例外を伝播せず401を返す", async () => {
    fetchMock.mockResolvedValueOnce(unauthorized()).mockRejectedValueOnce(new TypeError("Failed to fetch"));

    await expect(apiFetch("/posts")).rejects.toMatchObject({ status: 401 });
  });
});
