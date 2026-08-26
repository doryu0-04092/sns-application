// 投稿作成シナリオ(書き込み系)。
//
// 画像なしの投稿に限定する。画像ありにするとブラウザから S3 へ直接 PUT する経路になり、
// バックエンドを経由しないため「アプリの性能」とは別の話になる。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, JSON_HEADERS, SLEEP_SECONDS } from '../lib/config.ts';
import { ensureAuth } from '../lib/auth.ts';
import { expectStatus } from '../lib/checks.ts';
import { buildOptions } from '../profiles/index.ts';

export const options = buildOptions({
  'http_req_duration{name:POST /posts}': ['p(95)<800'],
});

export default function (): void {
  ensureAuth();

  // body は 280 文字上限(V3 の VARCHAR(280) と Bean Validation)。余裕を持たせる。
  const body = `perf write VU${__VU} ITER${__ITER} ${Date.now()} ${'x'.repeat(40)}`;

  const res = http.post(`${BASE_URL}/posts`, JSON.stringify({ body, imageKeys: [] }), {
    headers: JSON_HEADERS,
    tags: { name: 'POST /posts' },
  });
  expectStatus(res, 201, 'POST /posts');

  if (SLEEP_SECONDS > 0) sleep(SLEEP_SECONDS);
}
