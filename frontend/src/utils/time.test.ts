import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatRelativeTime } from "./time";

/**
 * 相対時刻表示の境界値テスト。
 *
 * Date.now() に依存するため、システム時刻を固定して実行する。
 * 固定しないと「たった今」と「1分」の境目でだけ落ちるテストになる。
 */
describe("formatRelativeTime", () => {
  const NOW = new Date("2026-06-15T12:00:00");

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(NOW);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /**
   * NOW から指定秒数だけ過去の日時文字列を作る。
   *
   * バックエンドはUTCであることを明示した "2026-06-15T12:00:00.000000Z" 形式で返すため、
   * toISOString() の出力をそのまま使える(どちらも末尾Zで、new Date() はUTCとして解釈する)。
   *
   * 以前はタイムゾーン指定子の無い形式に合わせてローカルの日時要素から組み立てていたが、
   * それは「オフセットの無い日時をローカル時刻として解釈する」という
   * 実装側の不具合にテストを合わせてしまっており、ずれを検出できない状態だった。
   */
  function secondsAgo(seconds: number): string {
    return new Date(NOW.getTime() - seconds * 1000).toISOString();
  }

  it.each([
    ["0秒前", 0, "たった今"],
    ["59秒前", 59, "たった今"],
    ["60秒前", 60, "1分"],
    ["61秒前", 61, "1分"],
  ])("%s は %s と表示される", (_label, seconds, expected) => {
    expect(formatRelativeTime(secondsAgo(seconds))).toBe(expected);
  });

  it.each([
    ["59分前", 59 * 60, "59分"],
    ["60分前", 60 * 60, "1時間"],
    ["23時間前", 23 * 60 * 60, "23時間"],
    ["24時間前", 24 * 60 * 60, "1日"],
    ["6日前", 6 * 24 * 60 * 60, "6日"],
  ])("%s は %s と表示される", (_label, seconds, expected) => {
    expect(formatRelativeTime(secondsAgo(seconds))).toBe(expected);
  });

  /** 7日を境に相対表記から日付表記へ切り替わる。 */
  it("7日前からは日付表記になる", () => {
    expect(formatRelativeTime(secondsAgo(7 * 24 * 60 * 60))).toBe("2026/06/08");
  });

  it("かなり古い日付も日付表記になる", () => {
    expect(formatRelativeTime("2025-01-05T09:30:00.000000Z")).toBe("2025/01/05");
  });

  /** 月日は必ず2桁でゼロ埋めされる。 */
  it("月日は2桁にゼロ埋めされる", () => {
    expect(formatRelativeTime("2026-01-02T09:30:00.000000Z")).toBe("2026/01/02");
  });

  /**
   * 未来の日時(サーバーとクライアントの時計のずれ等)は差分が負になる。
   * diffSec < 60 の条件に入るため「たった今」になる。
   */
  it("未来の日時はたった今と表示される", () => {
    expect(formatRelativeTime(secondsAgo(-3600))).toBe("たった今");
  });
});
