// ユーザー検索シナリオ。
//
// 狙って踏むコード: UserMapper の display_name ILIKE '%' || ? || '%'
//   両端ワイルドカードのため B-tree インデックスが使えず、
//   かつ display_name にはそもそもインデックスが無い(V1__init.sql)。
//   ユーザー数に比例してフルスキャンのコストが増えるはずで、それを確認する。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, PAGE_LIMIT, SEARCH_TERMS, pick } from '../lib/config.js';
import { ensureAuth } from '../lib/auth.js';
import { expectStatus } from '../lib/checks.js';
import { buildOptions } from '../profiles/index.js';

export const options = buildOptions({
  'http_req_duration{name:GET /users?q=}': ['p(95)<800'],
});

export default function () {
  ensureAuth();

  const term = pick(SEARCH_TERMS);
  const res = http.get(
    `${BASE_URL}/users?q=${encodeURIComponent(term)}&limit=${PAGE_LIMIT}`,
    { tags: { name: 'GET /users?q=' } },
  );
  expectStatus(res, 200, 'GET /users?q=');

  sleep(1);
}
