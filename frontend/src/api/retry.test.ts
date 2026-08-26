import { describe, expect, it } from "vitest";
import { ApiError } from "./client";
import { shouldRetryQuery } from "./retry";

/**
 * 再試行の判定。
 *
 * 「再試行しても結果が変わらない失敗」を弾けているかが要点で、
 * 回数の上限そのものは TanStack Query の既定値に合わせているだけ。
 */
describe("shouldRetryQuery", () => {
  it.each([
    ["400 バリデーションエラー", 400],
    ["401 未認証", 401],
    ["403 権限なし", 403],
    ["404 見つからない", 404],
    ["409 競合", 409],
    ["499 4xxの上端", 499],
  ])("%s は再試行しない", (_label, status) => {
    expect(shouldRetryQuery(0, new ApiError("CODE", "message", status))).toBe(false);
  });

  it.each([
    ["500 サーバーエラー", 500],
    ["503 一時的に利用不可", 503],
  ])("%s は再試行する", (_label, status) => {
    expect(shouldRetryQuery(0, new ApiError("CODE", "message", status))).toBe(true);
  });

  /** ApiError にならない失敗(通信断など)は一時的な可能性があるため再試行する。 */
  it("ApiError でない失敗は再試行する", () => {
    expect(shouldRetryQuery(0, new TypeError("Failed to fetch"))).toBe(true);
  });

  it.each([
    [0, true],
    [1, true],
    [2, true],
    [3, false],
    [4, false],
  ])("失敗%d回目の再試行は %s", (failureCount, expected) => {
    expect(shouldRetryQuery(failureCount, new Error("boom"))).toBe(expected);
  });

  /**
   * 4xx は回数に関係なく即座に打ち切る。
   * 回数上限に達したから止まったのではないことを固定する。
   */
  it("4xx は1回目の失敗でも再試行しない", () => {
    expect(shouldRetryQuery(0, new ApiError("POST_NOT_FOUND", "投稿が見つかりません", 404))).toBe(false);
  });
});
