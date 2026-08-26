/** 長辺の上限。投稿詳細は幅600px程度で表示されるため、2倍の画面でも足りる値にしている。 */
const MAX_EDGE = 1600;

/** 再エンコード時の品質。写真で劣化が目立たず、かつ十分に軽くなる値。 */
const QUALITY = 0.85;

/**
 * アニメーションGIFは1コマ目だけになってしまうため、canvas を通さない。
 * SVG など canvas で扱うと危険・無意味な形式も対象外にする。
 */
const RESIZABLE_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

/**
 * アップロード前に画像を縮小する。
 *
 * <b>なぜ必要か</b>: E2Eテストで、投稿1枚に 1600x1200 (573KB) と 1200x900 (369KB) を添付すると
 * 約921KBを転送していた。Chrome の「画像配信の改善」も 926.5kB の削減余地を指摘している。
 * 原寸のまま保存しているため、スマートフォンで撮った数MBの写真がそのまま配信される。
 *
 * <b>方針</b>: 長辺が MAX_EDGE を超えるものだけ縮小する。
 * それ以下の画像は再エンコードしない。開くたびに劣化を重ねても得るものが無いため。
 *
 * <b>形式は変えない</b>。PNG を JPEG にすると軽くはなるが透過が失われ、
 * 透過アイコンを上げた利用者の画像が黒く潰れる。軽さのために見た目を壊さない。
 *
 * 失敗した場合は元のファイルをそのまま返す。
 * 縮小はあくまで最適化であり、これが原因で投稿できなくなる方が損失が大きい。
 */
export async function downscaleImage(file: File): Promise<File> {
  if (!RESIZABLE_TYPES.has(file.type)) return file;

  try {
    const bitmap = await createImageBitmap(file);
    const longest = Math.max(bitmap.width, bitmap.height);

    if (longest <= MAX_EDGE) {
      bitmap.close();
      return file;
    }

    const scale = MAX_EDGE / longest;
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);

    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) {
      bitmap.close();
      return file;
    }
    context.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();

    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, file.type, QUALITY);
    });
    if (!blob) return file;

    // 縮小したのに大きくなる場合(既に強く圧縮された画像など)は元を使う。
    if (blob.size >= file.size) return file;

    return new File([blob], file.name, { type: file.type, lastModified: file.lastModified });
  } catch {
    return file;
  }
}
