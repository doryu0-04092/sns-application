package com.snsapp.backend.common;

/**
 * 投稿・コメント本文の長さ制約(F-04/F-07/F-08/F-21/F-22)。
 *
 * <p>投稿・コメントの作成/編集で計4つのリクエストDTOが同じ上限を持つため、
 * 値と文言をここに集約している。DB側(posts.body/comments.body の VARCHAR)と一致させること。
 */
public final class ContentLimits {

    /** 投稿・コメント本文の最大文字数。 */
    public static final int MAX_BODY_LENGTH = 280;

    /**
     * 本文の長さ違反時にユーザーへ返すメッセージ。
     * アノテーションの属性値に使うためコンパイル時定数であること。
     */
    public static final String BODY_MESSAGE = "本文は1文字以上280文字以内で入力してください";

    private ContentLimits() {
    }
}
