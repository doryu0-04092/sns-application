import { Link } from "react-router-dom";
import type { Post } from "../types/post";
import { FollowButton } from "./FollowButton";
import { formatRelativeTime } from "../utils/time";

export function PostCard({ post }: { post: Post }) {
  return (
    <article className="flex gap-3 border-b border-gray-200 px-4 py-3">
      <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 font-bold text-white">
        {post.authorDisplayName.charAt(0)}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-1.5 text-sm">
          <span className="font-bold">{post.authorDisplayName}</span>
          <span className="text-gray-500">{formatRelativeTime(post.createdAt)}</span>
          {!post.isMine && (
            <FollowButton userId={post.authorId} isFollowing={post.isFollowing} className="ml-auto" />
          )}
        </div>
        <Link
          to={`/posts/${post.id}`}
          className="mt-1 block whitespace-pre-wrap text-[15px] leading-normal text-inherit"
        >
          {post.body}
        </Link>
        <div className="mt-1 flex gap-7 text-sm text-gray-500">
          <Link to={`/posts/${post.id}`} className="flex items-center gap-1.5 hover:text-gray-800">
            <span>💬</span>
            <span>{post.commentCount}</span>
          </Link>
        </div>
      </div>
    </article>
  );
}
