import { apiFetch } from "./client";
import type { CursorPage } from "../types/post";
import type { Profile, UpdateProfilePayload, UserSummary } from "../types/user";
import type { User } from "../types/auth";

export function getProfile(userId: number): Promise<Profile> {
  return apiFetch<Profile>(`/users/${userId}`);
}

export function updateProfile(payload: UpdateProfilePayload): Promise<User> {
  return apiFetch<User>("/users/me", { method: "PATCH", body: JSON.stringify(payload) });
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
