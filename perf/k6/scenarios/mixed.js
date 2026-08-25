// 混合シナリオ。ストレス・耐久・スパイクで使う。
//
// 単一エンドポイントを叩き続けても「実際に使われたときに何が起きるか」は分からない。
// 閲覧・詳細・検索・書き込みを実利用に近い比率で混ぜ、
// 読み書きが同時に走ったときのロック競合やプール競合まで含めて負荷をかける。
//
// 比率: フィード閲覧 8 / 投稿詳細 1.5 / 検索 0.5 / 書き込み 0.5(合計10.5を正規化)

import { sleep } from 'k6';
import http from 'k6/http';
import {
  BASE_URL, PASSWORD, USER_COUNT, PAGE_LIMIT,
  HOT_POST_IDS, SEARCH_TERMS, JSON_HEADERS, SLEEP_SECONDS, pick,
} from '../lib/config.js';
import { ensureAuth } from '../lib/auth.js';
import { expectStatus, dataOf } from '../lib/checks.js';
import { buildOptions, selectProfile } from '../profiles/index.js';

const profile = selectProfile();

export const options = buildOptions({
  'http_req_duration{name:GET /posts feed=all}': ['p(95)<500', 'p(99)<1000'],
  'http_req_duration{name:GET /posts/{id}/comments}': ['p(95)<500'],
  'http_req_duration{name:GET /users?q=}': ['p(95)<800'],
  'http_req_duration{name:POST /posts}': ['p(95)<800'],
});

// 事前認証に使うユーザー数の上限。setup がこの数だけ順番にログインする。
const PREAUTH_USERS = Math.min(USER_COUNT, 50);

function loginAs(email) {
  return http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password: PASSWORD }),
    { headers: JSON_HEADERS },
  );
}

export function setup() {
  const bootstrap = loginAs('perf_1@example.test');
  if (bootstrap.status !== 200) {
    throw new Error(`setup: login failed with ${bootstrap.status}`);
  }

  const feed = http.get(`${BASE_URL}/posts?feed=all&limit=50`);
  if (feed.status !== 200) {
    throw new Error(`setup: feed fetch failed with ${feed.status}`);
  }
  const normalIds = feed.json().data.items
    .map((p) => p.id)
    .filter((id) => !HOT_POST_IDS.includes(id));

  // ---------------------------------------------------------------------
  // 事前認証(profile.preAuth が true のときだけ)
  //
  // なぜ必要か: ストレス・スパイクでは VU が最大300まで増える。
  // 各VUが自前でログインすると BCrypt(cost 10)が300回走り、
  // 「何が飽和したのか」の答えが BCrypt になってしまう。
  // それはランプアップの副作用であって、測りたかったものではない。
  //
  // そこで setup で先にログインし、auth_token だけを各VUの cookie jar に配る。
  // 全VUを同一ユーザーにしないのは、isMine / isFollowing / isLiked の計算結果が
  // キャッシュに乗って実際より速い数字が出るのを避けるため。
  //
  // refresh_token は配らない。リフレッシュはローテーション方式で、
  // 同じトークンを複数VUが使うと AuthController が盗用と判定して
  // そのユーザーの全トークンを一括失効させてしまう(=測定が壊れる)。
  // 15分を超える耐久テストは preAuth を使わず、各VUに自前でログインさせる。
  // ---------------------------------------------------------------------
  if (!profile.preAuth) {
    return { normalIds, hotIds: HOT_POST_IDS, tokens: null };
  }

  const tokens = [];
  for (let i = 1; i <= PREAUTH_USERS; i++) {
    const res = loginAs(`perf_${i}@example.test`);
    if (res.status !== 200) {
      throw new Error(`setup: preauth login failed for perf_${i} with ${res.status}`);
    }
    const cookie = res.cookies['auth_token'];
    if (!cookie || cookie.length === 0) {
      throw new Error(`setup: auth_token cookie missing for perf_${i}`);
    }
    tokens.push(cookie[0].value);
  }
  return { normalIds, hotIds: HOT_POST_IDS, tokens };
}

let cookieInstalled = false;

function authenticate(data) {
  if (!data.tokens) {
    ensureAuth();
    return;
  }
  // モジュールスコープの変数は VU ごとに独立するので、VU あたり1回で済む。
  if (!cookieInstalled) {
    const token = data.tokens[(__VU - 1) % data.tokens.length];
    http.cookieJar().set(BASE_URL, 'auth_token', token);
    cookieInstalled = true;
  }
}

function browseFeed() {
  const res = http.get(`${BASE_URL}/posts?feed=all&limit=${PAGE_LIMIT}`, {
    tags: { name: 'GET /posts feed=all' },
  });
  if (!expectStatus(res, 200, 'GET /posts feed=all')) return;

  const page = dataOf(res);
  if (page && page.nextCursor && Math.random() < 0.5) {
    const next = http.get(
      `${BASE_URL}/posts?feed=all&limit=${PAGE_LIMIT}&cursor=${page.nextCursor}`,
      { tags: { name: 'GET /posts feed=all' } },
    );
    expectStatus(next, 200, 'GET /posts feed=all (page2)');
  }
}

function openDetail(data) {
  const useHot = data.hotIds.length > 0 && Math.random() < 0.33;
  const pool = useHot ? data.hotIds : data.normalIds;
  if (!pool || pool.length === 0) return;
  const postId = pick(pool);

  const detail = http.get(`${BASE_URL}/posts/${postId}`, { tags: { name: 'GET /posts/{id}' } });
  expectStatus(detail, 200, 'GET /posts/{id}');

  const comments = http.get(`${BASE_URL}/posts/${postId}/comments`, {
    tags: { name: 'GET /posts/{id}/comments', variant: useHot ? 'hot' : 'normal' },
  });
  expectStatus(comments, 200, 'GET /posts/{id}/comments');
}

function search() {
  const res = http.get(
    `${BASE_URL}/users?q=${encodeURIComponent(pick(SEARCH_TERMS))}&limit=${PAGE_LIMIT}`,
    { tags: { name: 'GET /users?q=' } },
  );
  expectStatus(res, 200, 'GET /users?q=');
}

function write() {
  const body = `perf mixed VU${__VU} ITER${__ITER} ${Date.now()}`;
  const res = http.post(`${BASE_URL}/posts`, JSON.stringify({ body, imageKeys: [] }), {
    headers: JSON_HEADERS,
    tags: { name: 'POST /posts' },
  });
  expectStatus(res, 201, 'POST /posts');
}

export default function (data) {
  authenticate(data);

  const roll = Math.random() * 10.5;
  if (roll < 8) {
    browseFeed();
  } else if (roll < 9.5) {
    openDetail(data);
  } else if (roll < 10) {
    search();
  } else {
    write();
  }

  if (SLEEP_SECONDS > 0) sleep(SLEEP_SECONDS);
}
