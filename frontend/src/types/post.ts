export interface Post {
  id: number;
  body: string | null;
  authorId: number;
  authorDisplayName: string;
  authorAvatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
  commentCount: number;
  likeCount: number;
  isMine: boolean;
  isFollowing: boolean;
  isLiked: boolean;
  deleted: boolean;
}

export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
}

export type Feed = "all" | "following";
