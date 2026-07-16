export interface Comment {
  id: number;
  postId: number;
  parentCommentId: number | null;
  body: string | null;
  authorId: number;
  authorDisplayName: string;
  authorAvatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  isMine: boolean;
  isFollowing: boolean;
  isLiked: boolean;
  deleted: boolean;
}
