import type { CommentResponse } from "../api/generated";

/**
 * コメントの型はバックエンドのDTOから生成している(#39)。生成し直す: npm run gen:api
 *
 * 一覧は平坦な配列で返り、親子関係は parentCommentId で表される。
 * ツリーへの組み立てはクライアント側の責務(utils/commentTree.ts)。
 */
export type Comment = CommentResponse;
