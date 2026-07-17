import { Link } from "react-router-dom";
import type { Post } from "../types/post";
import { FollowButton } from "./FollowButton";
import { LikeButton } from "./LikeButton";
import { formatRelativeTime } from "../utils/time";

export function PostCard({ post }: { post: Post }) {
  return (
    <article className="flex gap-3 border-b border-gray-200 px-4 py-3">
      <Link
        to={`/users/${post.authorId}`}
        className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 font-bold text-white"
      >
        {post.authorDisplayName.charAt(0)}
      </Link>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5 text-sm">
          <Link to={`/users/${post.authorId}`} className="font-bold hover:underline">
            {post.authorDisplayName}
          </Link>
          <span className="text-gray-500">{formatRelativeTime(post.createdAt)}</span>
          {!post.deleted && !post.isMine && (
            <FollowButton userId={post.authorId} isFollowing={post.isFollowing} className="ml-auto" />
          )}
        </div>
        {post.deleted ? (
          <Link
            to={`/posts/${post.id}`}
            className="mt-1 block rounded border border-dashed border-gray-300 bg-gray-50 px-3 py-2 text-[15px] italic leading-normal text-gray-400"
          >
            この投稿は削除されました(返信は保持されています)
          </Link>
        ) : (
          <Link
            to={`/posts/${post.id}`}
            className="mt-1 block whitespace-pre-wrap text-[15px] leading-normal text-inherit"
          >
            {post.body}
          </Link>
        )}
        <div className="mt-1 flex gap-7 text-sm text-gray-500">
          <Link to={`/posts/${post.id}`} className="flex items-center gap-1.5 hover:text-gray-800">
            <span>💬</span>
            <span>{post.commentCount}</span>
          </Link>
          {!post.deleted && (
            <LikeButton
              postId={post.id}
              isLiked={post.isLiked}
              likeCount={post.likeCount}
              disabled={post.isMine}
            />
          )}
        </div>
      </div>
    </article>
  );
}
