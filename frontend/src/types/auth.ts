import type { LoginRequest, SignupRequest, UserResponse } from "../api/generated";

/**
 * 認証関連の型はバックエンドのDTOから生成している(#39)。生成し直す: npm run gen:api
 */

/** ログイン中ユーザー自身の情報。メールアドレスを含むため、自分に関するAPIでのみ返る。 */
export type User = UserResponse;

export type SignupPayload = SignupRequest;

export type LoginPayload = LoginRequest;
