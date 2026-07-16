import { apiFetch } from "./client";
import type { CursorPage, Feed, Post } from "../types/post";

export function listPosts(params: {
  feed: Feed;
  cursor?: string | null;
  sinceId?: number | null;
  limit?: number;
}): Promise<CursorPage<Post>> {
  const query = new URLSearchParams({ feed: params.feed, limit: String(params.limit ?? 20) });
  if (params.cursor) query.set("cursor", params.cursor);
  if (params.sinceId != null) query.set("sinceId", String(params.sinceId));
  return apiFetch<CursorPage<Post>>(`/posts?${query.toString()}`);
}

export function createPost(body: string): Promise<Post> {
  return apiFetch<Post>("/posts", { method: "POST", body: JSON.stringify({ body }) });
}

export function getPost(postId: number): Promise<Post> {
  return apiFetch<Post>(`/posts/${postId}`);
}

export function updatePost(postId: number, body: string): Promise<Post> {
  return apiFetch<Post>(`/posts/${postId}`, { method: "PATCH", body: JSON.stringify({ body }) });
}

export function deletePost(postId: number): Promise<null> {
  return apiFetch<null>(`/posts/${postId}`, { method: "DELETE" });
}
