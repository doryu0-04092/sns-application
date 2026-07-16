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
  isMine: boolean;
  isFollowing: boolean;
  deleted: boolean;
}
