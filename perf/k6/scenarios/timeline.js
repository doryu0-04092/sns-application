// タイムライン閲覧シナリオ。
//
// 狙って踏むコード: PostMapper.findFeedAll
//   1投稿ごとに相関サブクエリが5本走る(comment_count / like_count / is_mine /
//   is_following / is_liked)。うち comment_count は内側でさらに EXISTS を回す二重ネスト。
//   limit=20 なので、1リクエストあたり最大100本のサブクエリが評価される計算になる。

import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, PAGE_LIMIT } from '../lib/config.js';
import { ensureAuth } from '../lib/auth.js';
import { expectStatus, dataOf } from '../lib/checks.js';
import { buildOptions } from '../profiles/index.js';

export const options = buildOptions({
  'http_req_duration{name:GET /posts feed=all}': ['p(95)<500', 'p(99)<1000'],
});

export default function () {
  ensureAuth();

  // 1ページ目。フロントエンドの usePostsFeed.ts と同じ limit=20 で叩く。
  const first = http.get(`${BASE_URL}/posts?feed=all&limit=${PAGE_LIMIT}`, {
    tags: { name: 'GET /posts feed=all' },
  });
  if (!expectStatus(first, 200, 'GET /posts feed=all')) return;

  const page = dataOf(first);
  if (!page || !page.items || page.items.length === 0) return;

  // 2ページ目(無限スクロール1回分)。カーソル方式なので OFFSET のような
  // 「後ろのページほど遅くなる」性質は無いはずで、それを確認する意味もある。
  if (page.nextCursor) {
    const second = http.get(
      `${BASE_URL}/posts?feed=all&limit=${PAGE_LIMIT}&cursor=${page.nextCursor}`,
      { tags: { name: 'GET /posts feed=all' } },
    );
    expectStatus(second, 200, 'GET /posts feed=all (page2)');
  }

  // 閲覧中に一定確率でいいねを付け外しする。
  // 読み取り一辺倒だと likes テーブルへの書き込みが一切発生せず、
  // 読み書き混在時のロック競合が測れないため。
  // このAPIは冪等(同じ操作を2回投げても200)なので、状態を気にせず投げてよい。
  // いいね対象から除外すべきものが2つある。どちらもアプリの正しい挙動であり、
  // 除外しないとシナリオ側の不備によるエラーを「アプリのエラー率」として数えてしまう
  // (どちらもスモークテストで実際に踏んだ)。
  //   - deleted: 論理削除済みの投稿はコメントがあればフィードに残るが、
  //              いいねしようとすると 404 POST_NOT_FOUND になる。
  //              フロントエンドも削除済み投稿にはいいねボタンを出さない。
  //   - isMine:  自分の投稿へのいいねは 400 POST_SELF_LIKE_NOT_ALLOWED で拒否される。
  const likeable = page.items.filter((p) => !p.deleted && !p.isMine);
  if (likeable.length > 0 && Math.random() < 0.3) {
    const target = likeable[Math.floor(Math.random() * likeable.length)];
    const liked = http.post(`${BASE_URL}/posts/${target.id}/like`, null, {
      tags: { name: 'POST /posts/{id}/like' },
    });
    expectStatus(liked, 200, 'POST /posts/{id}/like');

    const unliked = http.del(`${BASE_URL}/posts/${target.id}/like`, null, {
      tags: { name: 'DELETE /posts/{id}/like' },
    });
    expectStatus(unliked, 200, 'DELETE /posts/{id}/like');
  }

  // 実際の利用者は取得と同時に次の操作をしない。
  // sleep を入れないと1VUが際限なくリクエストを投げ、VU数と負荷が対応しなくなる。
  sleep(1);
}
