// フォロー中フィードのシナリオ。
//
// 狙って踏むコード: PostMapper.findFeedFollowing
//   timeline.ts と同じ相関サブクエリに加え、follows テーブルとの JOIN が1本増える。
//   timeline.ts との差分が、この JOIN のコストにあたる。
//   比較対象として同時に測ることに意味があるので、単独では読まないこと。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, PAGE_LIMIT, SLEEP_SECONDS } from '../lib/config.ts';
import type { CursorPage, PostSummary } from '../lib/config.ts';
import { ensureAuth } from '../lib/auth.ts';
import { expectStatus, dataOf } from '../lib/checks.ts';
import { buildOptions } from '../profiles/index.ts';

export const options = buildOptions({
  'http_req_duration{name:GET /posts feed=following}': ['p(95)<500', 'p(99)<1000'],
});

export default function (): void {
  ensureAuth();

  const first = http.get(`${BASE_URL}/posts?feed=following&limit=${PAGE_LIMIT}`, {
    tags: { name: 'GET /posts feed=following' },
  });
  if (!expectStatus(first, 200, 'GET /posts feed=following')) return;

  const page = dataOf<CursorPage<PostSummary>>(first);
  if (page && page.nextCursor) {
    const second = http.get(
      `${BASE_URL}/posts?feed=following&limit=${PAGE_LIMIT}&cursor=${page.nextCursor}`,
      { tags: { name: 'GET /posts feed=following' } },
    );
    expectStatus(second, 200, 'GET /posts feed=following (page2)');
  }

  if (SLEEP_SECONDS > 0) sleep(SLEEP_SECONDS);
}
