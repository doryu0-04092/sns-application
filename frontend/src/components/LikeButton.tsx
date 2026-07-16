import { useMutation, useQueryClient } from "@tanstack/react-query";
import { likePost, unlikePost } from "../api/likes";
import { flipLikeInCaches } from "../api/queryKeys";
import { ApiError } from "../api/client";

interface LikeButtonProps {
  postId: number;
  isLiked: boolean;
  likeCount: number;
  disabled?: boolean;
}

export function LikeButton({ postId, isLiked, likeCount, disabled }: LikeButtonProps) {
  const queryClient = useQueryClient();

  const onSettled = (liked: boolean) => {
    flipLikeInCaches(queryClient, postId, liked);
  };

  const likeMutation = useMutation({
    mutationFn: () => likePost(postId),
    onSuccess: () => onSettled(true),
  });

  const unlikeMutation = useMutation({
    mutationFn: () => unlikePost(postId),
    onSuccess: () => onSettled(false),
  });

  const isPending = likeMutation.isPending || unlikeMutation.isPending;
  const failedMutation = likeMutation.isError ? likeMutation : unlikeMutation.isError ? unlikeMutation : null;

  if (disabled) {
    return (
      <span className="flex items-center gap-1.5 text-gray-500">
        <span>🤍</span>
        <span>{likeCount}</span>
      </span>
    );
  }

  return (
    <span className="inline-flex flex-col items-start">
      <button
        type="button"
        onClick={() => (isLiked ? unlikeMutation.mutate() : likeMutation.mutate())}
        disabled={isPending}
        className={`flex items-center gap-1.5 hover:text-red-600 disabled:opacity-50 ${
          isLiked ? "text-red-600" : "text-gray-500"
        }`}
      >
        <span>{isLiked ? "❤️" : "🤍"}</span>
        <span>{likeCount}</span>
      </button>
      {failedMutation && (
        <span className="mt-1 text-xs text-red-600">
          {failedMutation.error instanceof ApiError ? failedMutation.error.message : "処理に失敗しました"}
        </span>
      )}
    </span>
  );
}
