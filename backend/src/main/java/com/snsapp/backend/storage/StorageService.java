package com.snsapp.backend.storage;

/**
 * 画像ファイルの保存・配信を抽象化するインターフェース。
 *
 * <p><b>画像本体はバックエンドを経由しない。</b>ブラウザが {@link #createUploadUrl} で得た署名付きURLへ
 * 直接PUTし、バックエンドはキーだけを受け取ってDBに保存する。表示時は {@link #presignedGetUrl} で
 * 都度URLを生成する。バケットは非公開のため、署名なしでは画像を取得できない。
 *
 * <p>DBに保存するのはURLではなく<b>キー</b>({@code posts/uuid.jpg} 等)である点に注意。
 * 署名付きURLには有効期限が埋め込まれるため、保存してしまうと期限切れで使えなくなる。
 */
public interface StorageService {

    /**
     * アップロード用の署名付きURLを発行する。
     *
     * <p>キーは {@code pending/} 配下に採番される。投稿に紐づいた時点で
     * {@link #promote} で正式な場所へ移動させ、移動されなかったものは
     * ライフサイクルルールで自動削除される。
     *
     * @param contentType アップロードするファイルのContent-Type。署名に含まれるため、
     *                    クライアントは同じ値で送らないとS3に拒否される
     * @throws com.snsapp.backend.exception.InvalidImageTypeException 対応形式でない場合
     */
    PresignedUpload createUploadUrl(String contentType);

    /**
     * アップロードされた実物を検証し、{@code pending/} から {@code category} 配下へ移動する。
     *
     * <p>ブラウザが直接アップロードするため、バックエンドは中身を検証できていない。
     * ここでサイズ・Content-Typeを実際に確認してから正式な場所へ移すことで、
     * 署名時のチェックをすり抜けたオブジェクトがDBに登録されるのを防ぐ。
     *
     * @param pendingKey {@link #createUploadUrl} が返したキー
     * @param category   移動先の分類("posts" や "avatars")
     * @return 移動後のキー。これをDBに保存する
     * @throws com.snsapp.backend.exception.InvalidImageTypeException 未アップロード・対応外の形式の場合
     * @throws com.snsapp.backend.exception.ImageTooLargeException    サイズ上限を超えている場合
     */
    String promote(String pendingKey, String category);

    /**
     * 保存済みキーから、表示用の署名付きGET URLを生成する。
     * {@code key} が null の場合は null を返す(アイコン未設定などをそのまま扱えるようにするため)。
     */
    String presignedGetUrl(String key);

    /**
     * キーのオブジェクトを削除する(ベストエフォート)。
     * null や自分の管理下にないキーが渡された場合は何もせず、失敗しても例外は投げない。
     */
    void delete(String key);
}
