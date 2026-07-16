import { apiFetch } from "./client";

export function followUser(userId: number): Promise<null> {
  return apiFetch<null>(`/users/${userId}/follow`, { method: "POST" });
}

export function unfollowUser(userId: number): Promise<null> {
  return apiFetch<null>(`/users/${userId}/follow`, { method: "DELETE" });
}
