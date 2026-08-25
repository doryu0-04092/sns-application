// ログインのみのシナリオ。
//
// 他のシナリオが「VUごとに1回だけログインする」設計にしているため、
// ログイン自体のコストはそちらの数字には現れない。ここで独立して測る。
//
// パスワード検証は BCrypt(cost 10)。これは意図的に遅く作られたアルゴリズムであり、
// 遅いこと自体は脆弱性ではなく設計通りの挙動である。
// レポートでは「他のエンドポイントと同じ基準で評価してはいけない値」として扱う。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, PASSWORD, JSON_HEADERS, SLEEP_SECONDS } from '../lib/config.js';
import { userEmailForVU } from '../lib/auth.js';
import { expectStatus } from '../lib/checks.js';
import { buildOptions } from '../profiles/index.js';

export const options = buildOptions({
  'http_req_duration{name:POST /auth/login}': ['p(95)<1500'],
});

export default function () {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: userEmailForVU(), password: PASSWORD }),
    { headers: JSON_HEADERS, tags: { name: 'POST /auth/login' } },
  );
  expectStatus(res, 200, 'POST /auth/login');

  if (SLEEP_SECONDS > 0) sleep(SLEEP_SECONDS);
}
