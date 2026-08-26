import { apiFetch } from "./client";
import { downscaleImage } from "../utils/image";

interface PresignedUpload {
  key: string;
  uploadUrl: string;
}

/**
 * 画像をS3へ直接アップロードし、投稿作成に渡すキーの配列を返す。
 *
 * 画像本体はバックエンドを経由しない。バックエンドからは署名付きURLだけを受け取り、
 * ブラウザがS3へ直接PUTする。
 */
export async function uploadImages(files: File[]): Promise<string[]> {
  if (files.length === 0) return [];

  // 署名を取る前に縮小する。署名は Content-Type に対して行われるため、
  // 縮小後に形式が変わっていると PUT が署名不一致で弾かれる。
  // downscaleImage は形式を変えないが、順序をこちらに固定して依存関係を明示しておく。
  const prepared = await Promise.all(files.map(downscaleImage));

  const uploads = await apiFetch<PresignedUpload[]>("/uploads/presign", {
    method: "POST",
    body: JSON.stringify({ contentTypes: prepared.map((file) => file.type) }),
  });

  await Promise.all(
    uploads.map(async (upload, index) => {
      const file = prepared[index];
      // S3への直接PUTでは apiFetch を使わない。credentials(Cookie)を送ると署名が壊れ、
      // Content-Type は署名時の値と完全に一致させる必要があるため。
      const res = await fetch(upload.uploadUrl, {
        method: "PUT",
        body: file,
        headers: { "Content-Type": file.type },
      });
      if (!res.ok) {
        throw new Error(`画像のアップロードに失敗しました (${res.status})`);
      }
    }),
  );

  return uploads.map((upload) => upload.key);
}
