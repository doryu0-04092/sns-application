import { apiFetch } from "./client";

export function likePost(postId: number): Promise<null> {
  return apiFetch<null>(`/posts/${postId}/like`, { method: "POST" });
}

export function unlikePost(postId: number): Promise<null> {
  return apiFetch<null>(`/posts/${postId}/like`, { method: "DELETE" });
}

export function likeComment(commentId: number): Promise<null> {
  return apiFetch<null>(`/comments/${commentId}/like`, { method: "POST" });
}

export function unlikeComment(commentId: number): Promise<null> {
  return apiFetch<null>(`/comments/${commentId}/like`, { method: "DELETE" });
}
