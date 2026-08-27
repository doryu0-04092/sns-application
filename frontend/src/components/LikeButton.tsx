import { useMutation, useQueryClient } from "@tanstack/react-query";
import { likePost, unlikePost } from "../api/likes";
import { flipLikeInCaches, postsKeys, type OptimisticContext } from "../api/queryKeys";
import { ApiError } from "../api/client";

interface LikeButtonProps {
  postId: number;
  isLiked: boolean;
  likeCount: number;
  disabled?: boolean;
}

export function LikeButton({ postId, isLiked, likeCount, disabled }: LikeButtonProps) {
  const queryClient = useQueryClient();

  /**
   * 押した瞬間にキャッシュを書き換え、失敗したら書き換える前の状態へ戻す。
   *
   * cancelQueries を先に呼ぶのは、飛行中の取得が楽観的な書き込みの「後」に解決して
   * キャッシュを上書きするのを防ぐため。これが漏れると「押した直後は反映されるのに
   * 数秒後に静かに元へ戻る」という、エラーの出ない不具合になる。
   */
  const applyOptimistically = async (liked: boolean): Promise<OptimisticContext> => {
    await queryClient.cancelQueries({ queryKey: postsKeys.all });
    return { rollback: flipLikeInCaches(queryClient, postId, liked) };
  };

  const likeMutation = useMutation<null, Error, void, OptimisticContext>({
    mutationFn: () => likePost(postId),
    onMutate: () => applyOptimistically(true),
    onError: (_error, _variables, context) => context?.rollback(),
  });

  const unlikeMutation = useMutation<null, Error, void, OptimisticContext>({
    mutationFn: () => unlikePost(postId),
    onMutate: () => applyOptimistically(false),
    onError: (_error, _variables, context) => context?.rollback(),
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
        data-testid="like-button"
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
