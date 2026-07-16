import type { Comment } from "../types/comment";

export interface CommentNode {
  comment: Comment;
  children: CommentNode[];
}

export function buildCommentTree(comments: Comment[]): CommentNode[] {
  const byParent = new Map<number | null, Comment[]>();
  comments.forEach((comment) => {
    const key = comment.parentCommentId;
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key)!.push(comment);
  });

  function build(parentId: number | null): CommentNode[] {
    const children = byParent.get(parentId) ?? [];
    return children.map((comment) => ({ comment, children: build(comment.id) }));
  }

  return build(null);
}
