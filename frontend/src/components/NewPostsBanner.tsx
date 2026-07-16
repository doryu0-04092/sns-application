interface NewPostsBannerProps {
  count: number;
  hasMore: boolean;
  onClick: () => void;
}

export function NewPostsBanner({ count, hasMore, onClick }: NewPostsBannerProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="block w-full border-b border-gray-200 bg-blue-50 px-4 py-2 text-center text-sm font-semibold text-blue-600 hover:bg-blue-100"
    >
      {count}
      {hasMore ? "+" : ""}件の新しい投稿があります
    </button>
  );
}
