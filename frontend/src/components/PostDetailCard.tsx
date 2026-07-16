import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { updatePost, deletePost } from "../api/posts";
import { postsKeys } from "../api/queryKeys";
import { ApiError } from "../api/client";
import { FollowButton } from "./FollowButton";
import { LikeButton } from "./LikeButton";
import { useCharCount } from "../hooks/useCharCount";
import { formatRelativeTime } from "../utils/time";
import type { CursorPage, Post } from "../types/post";

type InfiniteListData = { pages: CursorPage<Post>[]; pageParams: unknown[] };

function isInfiniteListData(data: unknown): data is InfiniteListData {
  return !!data && typeof data === "object" && "pages" in data;
}

export function PostDetailCard({ post }: { post: Post }) {
  const [isEditing, setIsEditing] = useState(false);
  const [editBody, setEditBody] = useState(post.body ?? "");
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { remaining, isOver } = useCharCount(editBody);

  const updateMutation = useMutation({
    mutationFn: (body: string) => updatePost(post.id, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(postsKeys.detail(post.id), updated);
      queryClient.setQueriesData({ queryKey: ["posts", "list"] }, (data: unknown) => {
        if (!isInfiniteListData(data)) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.map((p) => (p.id === post.id ? updated : p)),
          })),
        };
      });
      setIsEditing(false);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deletePost(post.id),
    onSuccess: () => {
      queryClient.setQueriesData({ queryKey: ["posts", "list"] }, (data: unknown) => {
        if (!isInfiniteListData(data)) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({ ...page, items: page.items.filter((p) => p.id !== post.id) })),
        };
      });
      queryClient.removeQueries({ queryKey: postsKeys.detail(post.id) });
      navigate("/home");
    },
  });

  const handleSave = () => {
    const trimmed = editBody.trim();
    if (!trimmed || isOver) return;
    updateMutation.mutate(trimmed);
  };

  const handleCancel = () => {
    setEditBody(post.body ?? "");
    setIsEditing(false);
  };

  const handleDelete = () => {
    if (window.confirm("この投稿を削除しますか?")) {
      deleteMutation.mutate();
    }
  };

  return (
    <div className="border-b border-gray-200 px-4 py-4">
      <div className="mb-3 flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 font-bold text-white">
          {post.authorDisplayName.charAt(0)}
        </div>
        <div className="font-bold">{post.authorDisplayName}</div>
        {!post.deleted && !post.isMine && (
          <FollowButton userId={post.authorId} isFollowing={post.isFollowing} className="ml-auto" />
        )}
        {!post.deleted && post.isMine && !isEditing && (
          <div className="ml-auto flex gap-3 text-sm text-gray-500">
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
        <p className="mb-2 text-sm text-red-600">
          {deleteMutation.error instanceof ApiError ? deleteMutation.error.message : "削除に失敗しました"}
        </p>
      )}

      {post.deleted ? (
        <p className="rounded border border-dashed border-gray-300 bg-gray-50 px-3 py-3 text-base italic text-gray-400">
          この投稿は削除されました(返信は保持されています)
        </p>
      ) : isEditing ? (
        <div>
          <textarea
            value={editBody}
            onChange={(e) => setEditBody(e.target.value)}
            rows={4}
            className="w-full rounded border border-gray-300 p-2 text-lg"
          />
          {updateMutation.isError && (
            <p className="text-sm text-red-600">
              {updateMutation.error instanceof ApiError ? updateMutation.error.message : "更新に失敗しました"}
            </p>
          )}
          <div className="mt-2 flex items-center justify-between">
            <span className={`text-xs ${isOver ? "text-red-600" : "text-gray-500"}`}>{remaining}文字</span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={handleCancel}
                className="rounded-full border border-gray-300 px-4 py-1.5 text-sm"
              >
                キャンセル
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={updateMutation.isPending || !editBody.trim() || isOver}
                className="rounded-full bg-blue-600 px-4 py-1.5 text-sm font-bold text-white disabled:opacity-50"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      ) : (
        <p className="whitespace-pre-wrap text-xl leading-normal">{post.body}</p>
      )}

      <div className="mt-3 text-sm text-gray-500">{formatRelativeTime(post.createdAt)}</div>
      <div className="mt-3 flex items-center gap-5 border-t border-b border-gray-200 py-3 text-sm text-gray-500">
        <span>
          <strong className="text-gray-900">{post.commentCount}</strong> コメント
        </span>
        <LikeButton
          postId={post.id}
          isLiked={post.isLiked}
          likeCount={post.likeCount}
          disabled={post.isMine}
        />
      </div>
    </div>
  );
}
