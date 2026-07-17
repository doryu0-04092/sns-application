package com.snsapp.backend.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 画像等のファイル保存を抽象化するインターフェース。
 * 現状は{@link LocalDiskStorageService}(ローカルディスク)のみ実装するが、
 * 呼び出し側は戻り値のURL文字列しか意識しないため、将来S3実装に差し替える際も
 * 呼び出し側のコード変更は不要(docs/tech-stack.mdの設計方針)。
 */
public interface StorageService {

    /**
     * ファイルを保存し、公開URLを返す。
     *
     * @param file     保存対象のファイル
     * @param category 保存先の分類("posts"や"avatars"などのサブディレクトリ相当)
     * @return 保存後にアクセス可能な公開URL
     */
    String store(MultipartFile file, String category);

    /**
     * 指定URLのファイルを削除する(ベストエフォート)。
     * 自分の管理下にないURL(nullや外部URLなど)が渡された場合は何もしない。
     * 失敗しても例外は投げない。
     */
    void delete(String url);
}
