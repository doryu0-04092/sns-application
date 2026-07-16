import { apiFetch } from "./client";
import type { Comment } from "../types/comment";

export function listComments(postId: number): Promise<Comment[]> {
  return apiFetch<Comment[]>(`/posts/${postId}/comments`);
}

export function createComment(
  postId: number,
  payload: { body: string; parentCommentId: number | null },
): Promise<Comment> {
  return apiFetch<Comment>(`/posts/${postId}/comments`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateComment(commentId: number, body: string): Promise<Comment> {
  return apiFetch<Comment>(`/comments/${commentId}`, { method: "PATCH", body: JSON.stringify({ body }) });
}

export function deleteComment(commentId: number): Promise<null> {
  return apiFetch<null>(`/comments/${commentId}`, { method: "DELETE" });
}
