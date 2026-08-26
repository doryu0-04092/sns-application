// ユーザー検索シナリオ。
//
// 狙って踏むコード: UserMapper の display_name ILIKE '%' || ? || '%'
//   両端ワイルドカードのため B-tree インデックスが使えず、
//   かつ display_name にはそもそもインデックスが無い(V1__init.sql)。
//   ユーザー数に比例してフルスキャンのコストが増えるはずだが、
//   実測ではユーザー5,000人でも p95 5.13ms で影響は出なかった
//   (docs/perf-test-report.md 8-2)。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, PAGE_LIMIT, SEARCH_TERMS, SLEEP_SECONDS, pick } from '../lib/config.ts';
import { ensureAuth } from '../lib/auth.ts';
import { expectStatus } from '../lib/checks.ts';
import { buildOptions } from '../profiles/index.ts';

export const options = buildOptions({
  'http_req_duration{name:GET /users?q=}': ['p(95)<800'],
});

export default function (): void {
  ensureAuth();

  const term = pick(SEARCH_TERMS);
  const res = http.get(
    `${BASE_URL}/users?q=${encodeURIComponent(term)}&limit=${PAGE_LIMIT}`,
    { tags: { name: 'GET /users?q=' } },
  );
  expectStatus(res, 200, 'GET /users?q=');

  if (SLEEP_SECONDS > 0) sleep(SLEEP_SECONDS);
}
