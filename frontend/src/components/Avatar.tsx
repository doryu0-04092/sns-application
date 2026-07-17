export function Avatar({
  avatarUrl,
  displayName,
  className = "h-11 w-11 text-base",
}: {
  avatarUrl: string | null | undefined;
  displayName: string;
  className?: string;
}) {
  if (avatarUrl) {
    return (
      <img
        src={avatarUrl}
        alt={displayName}
        className={`${className} flex-shrink-0 rounded-full object-cover`}
      />
    );
  }

  return (
    <div
      className={`${className} flex flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-purple-400 to-blue-400 font-bold text-white`}
    >
      {displayName.charAt(0)}
    </div>
  );
}
