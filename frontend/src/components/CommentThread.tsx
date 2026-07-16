import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateComment, deleteComment } from "../api/comments";
import { commentsKeys, postsKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";
import { FollowButton } from "./FollowButton";
import { CommentLikeButton } from "./CommentLikeButton";
import { CommentForm } from "./CommentForm";
import { useCharCount } from "../hooks/useCharCount";
import { formatRelativeTime } from "../utils/time";
import type { CommentNode } from "../utils/commentTree";

interface CommentThreadProps {
  node: CommentNode;
  postId: number;
}

export function CommentThread({ node, postId }: CommentThreadProps) {
  const { comment, children } = node;
  const [isEditing, setIsEditing] = useState(false);
  const [editBody, setEditBody] = useState(comment.body ?? "");
  const [isReplying, setIsReplying] = useState(false);
  const queryClient = useQueryClient();
  const { remaining, isOver } = useCharCount(editBody);

  const updateMutation = useMutation({
    mutationFn: (body: string) => updateComment(comment.id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: commentsKeys.list(postId) });
      setIsEditing(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteComment(comment.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: commentsKeys.list(postId) });
      queryClient.invalidateQueries({ queryKey: postsKeys.detail(postId) });
      queryClient.invalidateQueries({ queryKey: ["posts", "list"] });
    },
  });

  const handleSave = () => {
    const trimmed = editBody.trim();
    if (!trimmed || isOver) return;
    updateMutation.mutate(trimmed);
  };

  const handleCancel = () => {
    setEditBody(comment.body ?? "");
    setIsEditing(false);
  };

  const handleDelete = () => {
    if (window.confirm("このコメントを削除しますか?(返信は保持されます)")) {
      deleteMutation.mutate();
    }
  };

  return (
    <div className="py-3">
      <div className="flex gap-2.5">
        <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 text-xs font-bold text-white">
          {comment.authorDisplayName.charAt(0)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5 text-sm">
            <span className="font-bold">{comment.authorDisplayName}</span>
            <span className="text-gray-500">{formatRelativeTime(comment.createdAt)}</span>
            {!comment.deleted && !comment.isMine && (
              <FollowButton userId={comment.authorId} isFollowing={comment.isFollowing} className="ml-auto" />
            )}
            {!comment.deleted && comment.isMine && !isEditing && (
              <div className="ml-auto flex gap-2 text-xs text-gray-500">
                <button type="button" onClick={() => setIsEditing(true)} className="hover:text-blue-600">
                  編集
                </button>
                <button type="button" onClick={handleDelete} className="hover:text-red-600">
                  削除
                </button>
              </div>
            )}
          </div>

          {deleteMutation.isError && (
            <p className="mt-0.5 text-sm text-red-600">
              {deleteMutation.error instanceof ApiError ? deleteMutation.error.message : "削除に失敗しました"}
            </p>
          )}

          {comment.deleted ? (
            <p className="mt-0.5 rounded border border-dashed border-gray-300 bg-gray-50 px-2 py-1.5 text-sm italic text-gray-400">
              このコメントは削除されました
            </p>
          ) : isEditing ? (
            <div className="mt-1">
              <textarea
                value={editBody}
                onChange={(e) => setEditBody(e.target.value)}
                rows={2}
                className="w-full rounded border border-gray-300 p-2 text-sm"
              />
              {updateMutation.isError && (
                <p className="text-sm text-red-600">
                  {updateMutation.error instanceof ApiError ? updateMutation.error.message : "更新に失敗しました"}
                </p>
              )}
              <div className="mt-1 flex items-center justify-between">
                <span className={`text-xs ${isOver ? "text-red-600" : "text-gray-500"}`}>{remaining}</span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={handleCancel}
                    className="rounded-full border border-gray-300 px-3 py-1 text-xs"
                  >
                    キャンセル
                  </button>
                  <button
                    type="button"
                    onClick={handleSave}
                    disabled={updateMutation.isPending || !editBody.trim() || isOver}
                    className="rounded-full bg-blue-600 px-3 py-1 text-xs font-bold text-white disabled:opacity-50"
                  >
                    保存
                  </button>
                </div>
              </div>
            </div>
          ) : (
            <p className="mt-0.5 whitespace-pre-wrap text-sm leading-normal">{comment.body}</p>
          )}

          {!comment.deleted && (
            <div className="mt-1 flex items-center gap-4 text-xs text-gray-500">
              <button type="button" onClick={() => setIsReplying((v) => !v)} className="hover:text-blue-600">
                返信
              </button>
              <CommentLikeButton
                postId={postId}
                commentId={comment.id}
                isLiked={comment.isLiked}
                likeCount={comment.likeCount}
                disabled={comment.isMine}
              />
            </div>
          )}

          {isReplying && (
            <div className="mt-2">
              <CommentForm postId={postId} parentCommentId={comment.id} onDone={() => setIsReplying(false)} />
            </div>
          )}

          {children.length > 0 && (
            <div className="mt-2 border-l-2 border-gray-200 pl-3">
              {children.map((child) => (
                <CommentThread key={child.comment.id} node={child} postId={postId} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
