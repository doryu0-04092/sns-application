import { useState } from "react";

export function Avatar({
  avatarUrl,
  displayName,
  className = "h-11 w-11 text-base",
}: {
  avatarUrl: string | null | undefined;
  displayName: string;
  className?: string;
}) {
  // 読み込みに失敗したURLを覚えておき、頭文字表示へ切り替える。
  //
  // 失敗しても <img> を描画し続けると、丸い枠から alt テキストがはみ出し、
  // 破損アイコンと重なって表示が崩れる(E2Eテストで確認)。
  // 署名付きURLの有効期限は24時間なので、画面を開いたまま日をまたいだ場合や、
  // S3上のオブジェクトが失われた場合に実際に到達する。
  //
  // 真偽値ではなくURLを覚えるのは、プロフィール編集で新しいアイコンに差し替わったときに
  // 「前のURLで失敗した」状態を引きずらないため。
  const [failedUrl, setFailedUrl] = useState<string | null>(null);

  if (avatarUrl && avatarUrl !== failedUrl) {
    return (
      <img
        src={avatarUrl}
        alt={displayName}
        onError={() => setFailedUrl(avatarUrl)}
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
