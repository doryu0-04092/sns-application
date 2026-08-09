import type { ProfileResponse, UpdateProfileRequest, UserSummaryResponse } from "../api/generated";

/**
 * ユーザー関連の型はバックエンドのDTOから生成している(#39)。生成し直す: npm run gen:api
 */
export type Profile = ProfileResponse;

/**
 * 一覧表示用のユーザー1件。
 *
 * `id` と `userId` の意味が異なる点に注意。`id` はページネーション用のレコードIDで、
 * フォロワー/フォロー中一覧ではフォロー関係のIDが入る。画面遷移には `userId` を使う。
 */
export type UserSummary = UserSummaryResponse;

/** プロフィール編集の送信内容。displayName は必須で、省略できるのは bio と avatarKey のみ。 */
export type UpdateProfilePayload = UpdateProfileRequest;
