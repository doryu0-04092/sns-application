export interface Profile {
  id: number;
  displayName: string;
  bio: string | null;
  avatarUrl: string | null;
  followerCount: number;
  followingCount: number;
  isMine: boolean;
  isFollowing: boolean;
}

export interface UserSummary {
  id: number;
  userId: number;
  displayName: string;
  avatarUrl: string | null;
  isFollowing: boolean;
}

export interface UpdateProfilePayload {
  displayName: string;
  bio: string;
  avatar?: File | null;
}
