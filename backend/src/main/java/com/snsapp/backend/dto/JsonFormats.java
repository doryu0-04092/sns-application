package com.snsapp.backend.dto;

/** APIレスポンスの表記を揃えるための定数。 */
public final class JsonFormats {

    /**
     * 日時をUTCとして表記するためのパターン。末尾の {@code 'Z'} はリテラルである。
     *
     * <p><b>なぜ必要か</b>: 以前はタイムゾーン指定子の無い {@code "2026-08-26T07:42:28.430169"} を返していた。
     * ECMAScript は<b>オフセットを持たない日時形式をローカル時刻として解釈する</b>ため、
     * ブラウザ側の {@code new Date(iso)} がUTCの値を現地時刻として読み、
     * JST環境では全ての日時が9時間ずれて表示されていた。
     *
     * <p><b>UTCだと言い切れる根拠</b>: 日時の値はすべてDBの {@code now()} 由来で、
     * DBセッションのタイムゾーンはUTCである。列は {@code TIMESTAMP}(タイムゾーン無し)のため、
     * {@code LocalDateTime} として読み出した値はUTCの壁時計時刻そのものになる。
     * したがってここで {@code Z} を付けることは、値の意味を変えずに欠けていた情報を補う操作にあたる。
     *
     * <p>列を {@code TIMESTAMPTZ} にすればこの前提自体を型で保証できるが、
     * 既存データの解釈変更を伴うため、本修正の範囲には含めていない。
     */
    public static final String UTC_DATE_TIME = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'";

    private JsonFormats() {}
}
