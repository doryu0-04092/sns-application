import http from 'k6/http';
import { BASE_URL, PASSWORD, USER_COUNT, JSON_HEADERS } from './config.ts';
import { expectStatus } from './checks.ts';
import type { K6Response } from './checks.ts';

// このアプリは Authorization ヘッダを見ず、httpOnly クッキー auth_token だけを読む
// (backend/src/main/java/com/snsapp/backend/security/JwtAuthFilter.java)。
// k6 の cookie jar は VU ごとに独立して自動保持されるため、クッキーを扱うコードは要らない。
// ただし k6 は既定でイテレーションごとに jar をリセットするため、
// profiles/index.ts の buildOptions で noCookiesReset: true を設定している。

// モジュールスコープの変数は VU ごとに別インスタンスになる(各VUが独立したJSランタイムを持つ)。
// そのため「この VU が最後に認証した時刻」をここに置ける。
let authenticatedAtMs = 0;

// アクセストークンの寿命は既定900秒(15分)。
// 30分の耐久テスト中に必ず失効するため、それより短い間隔でリフレッシュする。
const REAUTH_INTERVAL_MS = 10 * 60 * 1000;

/**
 * この VU が使うユーザーのメールアドレス。
 *
 * VU ごとに別ユーザーにするのは、全VUが同一ユーザーだと
 * isMine / isFollowing / isLiked の計算結果がキャッシュに乗り続け、
 * 実際より速い数字が出てしまうため。
 */
export function userEmailForVU(): string {
  const index = ((__VU - 1) % USER_COUNT) + 1;
  return `perf_${index}@example.test`;
}

export function login(): K6Response {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: userEmailForVU(), password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'POST /auth/login' } },
  );
  expectStatus(res, 200, 'POST /auth/login');
  return res;
}

export function refresh(): K6Response {
  // refresh_token クッキーは Path=/api/auth なので、このURLにだけ自動送信される。
  const res = http.post(`${BASE_URL}/auth/refresh`, null, {
    tags: { name: 'POST /auth/refresh' },
  });
  expectStatus(res, 200, 'POST /auth/refresh');
  return res;
}

/**
 * 必要なときだけ認証する。
 *
 * 毎イテレーションでログインしない理由: パスワード検証は BCrypt(cost 10) で、
 * 1回あたり数十〜数百ms の CPU を消費する(SecurityBeansConfig.java)。
 * 毎回呼ぶと測定値が BCrypt に支配され、本来測りたいクエリ性能が数字に現れない。
 * ログイン自体の性能は scenarios/login.ts で独立して測る。
 */
export function ensureAuth(): void {
  const now = Date.now();

  if (authenticatedAtMs === 0) {
    login();
    authenticatedAtMs = now;
    return;
  }

  if (now - authenticatedAtMs > REAUTH_INTERVAL_MS) {
    refresh();
    authenticatedAtMs = now;
  }
}
