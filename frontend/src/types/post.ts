import type { CursorPagePostResponse, PostResponse } from "../api/generated";

/**
 * 投稿の型はバックエンドのDTOから生成している(#39)。
 *
 * 以前はここに手書きしていたため、バックエンド側でフィールドを改名しても
 * 双方のテストが通ってしまい、実行時まで壊れに気づけなかった。
 * 生成物を唯一の出所にすることで、ズレという状態自体が起こらなくなる。
 *
 * 生成し直す: npm run gen:api
 */
export type Post = PostResponse;

/**
 * カーソルページネーションの1ページ分。
 *
 * 生成される型は要素の型ごとに実体化されている(CursorPagePostResponse など)ため、
 * 画面側で使いやすいようジェネリックに組み直す。
 * items 以外(nextCursor)は生成物から引き継ぐので、仕様が変われば追従する。
 */
export type CursorPage<T> = Omit<CursorPagePostResponse, "items"> & { items: T[] };

/**
 * タイムラインの種別。
 *
 * 仕様上は文字列パラメータで、許容値が列挙されていないため生成できない。
 * バックエンドは範囲外の値に 400 INVALID_FEED を返す。
 */
export type Feed = "all" | "following";
