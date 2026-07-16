import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { AppHeader } from "../components/AppHeader";
import { PostDetailCard } from "../components/PostDetailCard";
import { CommentForm } from "../components/CommentForm";
import { CommentThread } from "../components/CommentThread";
import { getPost } from "../api/posts";
import { listComments } from "../api/comments";
import { postsKeys, commentsKeys } from "../api/queryKeys";
import { buildCommentTree } from "../utils/commentTree";

export function PostDetailPage() {
  const { postId } = useParams();
  const id = Number(postId);

  const postQuery = useQuery({
    queryKey: postsKeys.detail(id),
    queryFn: () => getPost(id),
  });

  const commentsQuery = useQuery({
    queryKey: commentsKeys.list(id),
    queryFn: () => listComments(id),
    enabled: postQuery.isSuccess,
  });

  return (
    <div className="mx-auto min-h-screen max-w-xl border-x border-gray-200 bg-white">
      <AppHeader />
      {postQuery.isLoading && <p className="p-8 text-center text-gray-500">読み込み中...</p>}
      {postQuery.isError && <p className="p-8 text-center text-gray-500">この投稿は見つかりませんでした。</p>}
      {postQuery.data && (
        <>
          <PostDetailCard post={postQuery.data} />
          {!postQuery.data.deleted && (
            <div className="border-b-8 border-gray-100 px-4 py-3">
              <CommentForm postId={id} parentCommentId={null} submitLabel="返信する" placeholder="返信をポスト" />
            </div>
          )}
          <div className="border-b border-gray-200 px-4 py-2.5 text-sm font-bold text-gray-500">コメント</div>
          <div className="divide-y divide-gray-100 px-4">
            {commentsQuery.data &&
              buildCommentTree(commentsQuery.data).map((node) => (
                <CommentThread key={node.comment.id} node={node} postId={id} />
              ))}
          </div>
        </>
      )}
    </div>
  );
}
