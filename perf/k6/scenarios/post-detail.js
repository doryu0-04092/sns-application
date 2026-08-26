// 投稿詳細シナリオ。
//
// 狙って踏むコード: CommentMapper.findByPostId
//   このクエリには LIMIT が無く、投稿に紐づくコメントを常に全件返す
//   (API仕様としても「ページネーションは無い」と明記されている)。
//   さらに1コメントごとに相関サブクエリが3本走る。
//
//   平均的な投稿(コメント数個)だけを叩いてもこの設計の影響は数字に出ないため、
//   seed.sql が仕込んだ「コメント500件の投稿」を一定割合で混ぜる。
//   通常投稿とホット投稿の応答時間の差が、そのまま全件取得のコストになる。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, PAGE_LIMIT, HOT_POST_IDS, SLEEP_SECONDS, pick } from '../lib/config.js';
import { ensureAuth } from '../lib/auth.js';
import { expectStatus, dataOf } from '../lib/checks.js';
import { buildOptions } from '../profiles/index.js';

export const options = buildOptions({
  'http_req_duration{name:GET /posts/{id}/comments}': ['p(95)<500'],
  'http_req_duration{name:GET /posts/{id}}': ['p(95)<500'],

  // k6 は閾値を設定したサブメトリクスしかサマリに出さない。
  // hot(コメント約500件)と normal(数件)を別々の数字として取り出すために、
  // 意図的に閾値を置いている。この2つの差が LIMIT なし全件取得のコストそのものになる。
  // 値は「同じデータ量なら満たすはず」という基準ではなく、
  // 差を観測できるようにするための便宜的な上限である点に注意。
  'http_req_duration{name:GET /posts/{id}/comments,variant:normal}': ['p(95)<500'],
  'http_req_duration{name:GET /posts/{id}/comments,variant:hot}': ['p(95)<3000'],
});

// setup は全VUの開始前に1回だけ走る。ここで実在する投稿IDを集めておくことで、
// 各イテレーションが「まずフィードを引いてIDを得る」余計な1リクエストを省ける
// (それをやると詳細取得の数字にフィード取得のコストが混ざる)。
export function setup() {
  const login = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: 'perf_1@example.test', password: __ENV.PERF_PASSWORD || 'PerfTest1234!' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (login.status !== 200) {
    throw new Error(`setup: login failed with ${login.status}`);
  }

  const res = http.get(`${BASE_URL}/posts?feed=all&limit=50`);
  if (res.status !== 200) {
    throw new Error(`setup: feed fetch failed with ${res.status}`);
  }
  const page = res.json().data;
  const normalIds = page.items.map((p) => p.id).filter((id) => !HOT_POST_IDS.includes(id));

  return { normalIds, hotIds: HOT_POST_IDS };
}

export default function (data) {
  ensureAuth();

  // 3回に1回はコメントが極端に多い投稿を開く。
  const useHot = data.hotIds.length > 0 && Math.random() < 0.33;
  const pool = useHot ? data.hotIds : data.normalIds;
  if (!pool || pool.length === 0) return;
  const postId = pick(pool);

  // フロントエンドの PostDetailPage は詳細とコメントを別々に取得する。同じ形にする。
  const detail = http.get(`${BASE_URL}/posts/${postId}`, {
    tags: { name: 'GET /posts/{id}' },
  });
  expectStatus(detail, 200, 'GET /posts/{id}');

  const comments = http.get(`${BASE_URL}/posts/${postId}/comments`, {
    // ホット投稿と通常投稿を別タグにして、レポートで差を直接比較できるようにする。
    tags: { name: 'GET /posts/{id}/comments', variant: useHot ? 'hot' : 'normal' },
  });
  expectStatus(comments, 200, 'GET /posts/{id}/comments');

  if (SLEEP_SECONDS > 0) sleep(SLEEP_SECONDS);
}
