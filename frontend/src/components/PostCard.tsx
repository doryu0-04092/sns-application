import { Link } from "react-router-dom";
import type { Post } from "../types/post";
import { Avatar } from "./Avatar";
import { FollowButton } from "./FollowButton";
import { LikeButton } from "./LikeButton";
import { formatRelativeTime } from "../utils/time";

export function PostCard({ post }: { post: Post }) {
  return (
    <article className="flex gap-3 border-b border-gray-200 px-4 py-3">
      <Link to={`/users/${post.authorId}`} className="flex-shrink-0">
        <Avatar avatarUrl={post.authorAvatarUrl} displayName={post.authorDisplayName} />
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
        {!post.deleted && post.imageUrls.length > 0 && (
          <div
            className={`mt-2 grid gap-1 overflow-hidden rounded-xl ${
              post.imageUrls.length === 1 ? "grid-cols-1" : "grid-cols-2"
            }`}
          >
            {post.imageUrls.map((url) => (
              <img key={url} src={url} alt="" className="h-48 w-full object-cover" />
            ))}
          </div>
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
