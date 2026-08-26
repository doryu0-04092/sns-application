import { defineConfig, devices } from "@playwright/test";
import { API_BASE_URL, FRONTEND_URL } from "./e2e/support/stack";

/**
 * E2Eテストの設定。
 *
 * <b>位置づけ</b>: 単体テスト(vitest / jsdom)では原理的に検証できない領域だけを扱う。
 * HttpOnly クッキーの実送信、CORS プリフライト、S3 への実 PUT、canvas による画像縮小など、
 * ブラウザの実装そのものが要る箇所である(docs/test-plan.md の「E2Eの領域」)。
 *
 * <b>実行に必要なもの</b>: Docker だけ。
 * 専用スタック(docker-compose.e2e.yml)を globalSetup が起動し、globalTeardown が破棄する。
 * 開発用スタックとはポートもDBも別なので、`npm run dev` を動かしたままでも実行できる。
 * 永続ボリュームを持たないため、テストが作ったデータは破棄と同時に消える。
 *
 * <b>CIには載せていない</b>。載せるにはCI側でスタックの起動時間を丸ごと負担する必要があり、
 * 現状のCI構成から実行時間が大きく増えるため、まずローカルで回せる状態までを作っている。
 * ただし E2E コード自体は tsconfig.e2e.json 経由で `npm run build` の型検査対象にしてあり、
 * 型の壊れたテストがCIをすり抜けることはない。
 */
export default defineConfig({
  testDir: "./e2e",

  // テストごとに専用のユーザーと一意な本文を作るため、並列でも互いに干渉しない。
  fullyParallel: true,

  /**
   * 再試行は行わない。
   *
   * 再試行を入れると不安定なテストが「たまたま緑」になって隠れる。
   * このリポジトリではテストの緑が信用できることを重視しているため(docs/e2e-test-report.md の発端)、
   * 揺れは隠さずその場で見えるようにする。
   */
  retries: 0,

  // 失敗時に何が起きていたか後から追えるようにする。成功した実行では何も残さない。
  reporter: [["html", { open: "never" }], ["list"]],

  use: {
    baseURL: FRONTEND_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },

  // まずは Chromium のみ。firefox / webkit を足す場合はここに project を追加する。
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],

  globalSetup: "./e2e/support/global-setup.ts",
  globalTeardown: "./e2e/support/global-teardown.ts",

  webServer: {
    // 開発用の 5173 とは別ポートで起動する。--strictPort を付けているのは、
    // 塞がっていたときに黙って別ポートへ逃げると、
    // CORS の許可オリジンと食い違って原因の分かりにくい失敗になるため。
    command: "npm run dev -- --port 5273 --strictPort",
    url: FRONTEND_URL,
    // 既存の開発サーバを流用しない。E2E用スタックを向いている必要があるため。
    reuseExistingServer: false,
    // frontend/.env の値をここで上書きし、E2E用バックエンドを向かせる。
    env: { VITE_API_BASE_URL: API_BASE_URL },
    timeout: 120_000,
  },
});
