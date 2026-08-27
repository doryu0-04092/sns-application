import { useMutation, useQueryClient } from "@tanstack/react-query";
import { followUser, unfollowUser } from "../api/follows";
import { commentsKeys, flipFollowInCaches, postsKeys, usersKeys, type OptimisticContext } from "../api/queryKeys";
import { ApiError } from "../api/client";

interface FollowButtonProps {
  userId: number;
  isFollowing: boolean;
  className?: string;
}

export function FollowButton({ userId, isFollowing, className }: FollowButtonProps) {
  const queryClient = useQueryClient();

  /**
   * 押した瞬間にキャッシュを書き換え、失敗したら書き換える前の状態へ戻す。
   *
   * cancelQueries を先に呼ぶのは、飛行中の取得が楽観的な書き込みの「後」に解決して
   * キャッシュを上書きするのを防ぐため。これが漏れると「押した直後は反映されるのに
   * 数秒後に静かに元へ戻る」という、エラーの出ない不具合になる。
   */
  const applyOptimistically = async (following: boolean): Promise<OptimisticContext> => {
    // flipFollowInCaches が触る3系統をすべて止める。1つでも漏らすとその系統だけレースが残る。
    await Promise.all([
      queryClient.cancelQueries({ queryKey: postsKeys.all }),
      queryClient.cancelQueries({ queryKey: commentsKeys.all }),
      queryClient.cancelQueries({ queryKey: usersKeys.all }),
    ]);
    return { rollback: flipFollowInCaches(queryClient, userId, following) };
  };

  /**
   * フォロー中フィードだけは再取得する。
   *
   * 他はキャッシュの書き換えで足りるが、ここだけは足りない。
   * 新しくフォローした相手の過去の投稿は、そもそもキャッシュに存在しない要素であり、
   * 既存の要素を書き換えるだけでは追加できないため。
   */
  const refetchFollowingFeed = () => {
    queryClient.invalidateQueries({ queryKey: postsKeys.list("following") });
  };

  const followMutation = useMutation<null, Error, void, OptimisticContext>({
    mutationFn: () => followUser(userId),
    onMutate: () => applyOptimistically(true),
    onError: (_error, _variables, context) => context?.rollback(),
    onSuccess: refetchFollowingFeed,
  });

  const unfollowMutation = useMutation<null, Error, void, OptimisticContext>({
    mutationFn: () => unfollowUser(userId),
    onMutate: () => applyOptimistically(false),
    onError: (_error, _variables, context) => context?.rollback(),
    onSuccess: refetchFollowingFeed,
  });

  const isPending = followMutation.isPending || unfollowMutation.isPending;
  const failedMutation = followMutation.isError ? followMutation : unfollowMutation.isError ? unfollowMutation : null;

  return (
    <span className={`inline-flex flex-col items-end ${className ?? ""}`}>
      <button
        type="button"
        onClick={() => (isFollowing ? unfollowMutation.mutate() : followMutation.mutate())}
        disabled={isPending}
        className={`rounded-full px-3 py-1 text-xs font-semibold disabled:opacity-50 ${
          isFollowing ? "border border-gray-300 text-gray-900" : "bg-gray-900 text-white"
        }`}
      >
        {isFollowing ? "フォロー中" : "フォローする"}
      </button>
      {failedMutation && (
        <span className="mt-1 text-xs text-red-600">
          {failedMutation.error instanceof ApiError ? failedMutation.error.message : "処理に失敗しました"}
        </span>
      )}
    </span>
  );
}
