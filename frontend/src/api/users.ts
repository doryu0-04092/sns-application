import { apiFetch } from "./client";
import type { CursorPage } from "../types/post";
import type { Profile, UpdateProfilePayload, UserSummary } from "../types/user";
import type { User } from "../types/auth";

export function getProfile(userId: number): Promise<Profile> {
  return apiFetch<Profile>(`/users/${userId}`);
}

export function updateProfile(payload: UpdateProfilePayload): Promise<User> {
  const formData = new FormData();
  formData.append("displayName", payload.displayName);
  formData.append("bio", payload.bio);
  if (payload.avatar) formData.append("avatar", payload.avatar);
  return apiFetch<User>("/users/me", { method: "PATCH", body: formData });
}

/**
 * ユーザーを一覧・検索する(F-15)。
 * `query` が空文字なら絞り込みを行わず、全ユーザーを新着順で取得する。
 */
export function searchUsers(
  query: string,
  cursor?: string | null,
  limit = 20,
): Promise<CursorPage<UserSummary>> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (query) params.set("query", query);
  if (cursor) params.set("cursor", cursor);
  return apiFetch<CursorPage<UserSummary>>(`/users?${params.toString()}`);
}

export function listFollowers(
  userId: number,
  cursor?: string | null,
  limit = 20,
): Promise<CursorPage<UserSummary>> {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  return apiFetch<CursorPage<UserSummary>>(`/users/${userId}/followers?${query.toString()}`);
}

export function listFollowing(
  userId: number,
  cursor?: string | null,
  limit = 20,
): Promise<CursorPage<UserSummary>> {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  return apiFetch<CursorPage<UserSummary>>(`/users/${userId}/following?${query.toString()}`);
}
