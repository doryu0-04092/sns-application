package com.snsapp.backend.exception;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.common.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 例外をHTTPレスポンスへ変換し、あわせてログを出す。
 *
 * <p>ログレベルの考え方(全体方針は docs/operations.md):
 * <ul>
 *   <li>ERROR = 即時対応が必要。ここではcatch-all(想定外のバグ・DB障害等)のみ。</li>
 *   <li>WARN = 放置すると問題になりうる。405など、クライアント実装の誤りの兆候。</li>
 *   <li>INFO = 想定内の業務エラー。どこまで処理が進んだかの切り分けに使う。</li>
 *   <li>DEBUG = 入力ミスや存在しないパスへのアクセス。常時出すと量が多く、
 *       個別に見る価値も低いため本番(dockerプロファイル)では出さない。</li>
 * </ul>
 * requestId / userId はRequestLoggingFilterがMDCに載せているため、ここで明示しなくても
 * 全てのログに付く。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 想定内エラー。各ApiExceptionサブクラスが持つ code/message/status をそのままレスポンスへ反映する。
    // 原因の特定は例外クラス自体(exception/*NotFoundException等、各クラスにスロー元をコメント済み)を見ればよい。
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        log.info("api error {} {}", kv("code", ex.getCode()), kv("status", ex.getStatus().value()));
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    // Bean Validation(@NotBlank/@Size等、DTOのフィールドアノテーション)違反。
    // 複数フィールドが同時に不正でも先頭の1件だけを返す(フォーム全体を作り直すほどではないため)。
    // フィールドごとにエラーコードを分けていないのは意図的: フロントはメッセージをそのまま表示するのみで、
    // コード単位の分岐が不要なため(必要になれば ex.getBindingResult() から field 名を拾って拡張できる)。
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("入力内容を確認してください");
        // 入力値そのものは出さない(パスワード等が含まれうるため)。どの項目が弾かれたかだけ記録する。
        logInvalidInput("validation", ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getField)
                .orElse("unknown"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
    }

    // 必須クエリパラメータの欠落。Bean Validationと違いSpringが引数解決の時点で投げるため個別にハンドルする。
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "必須パラメータ「%s」が指定されていません".formatted(ex.getParameterName());
        logInvalidInput("missing-parameter", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
    }

    // パス変数/クエリパラメータの型不一致(例: Long のはずの {postId} に "abc" が来た)。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "パラメータ「%s」の形式が正しくありません".formatted(ex.getName());
        logInvalidInput("type-mismatch", ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
    }

    // リクエストボディが読み取れない(壊れたJSON、Content-Type不一致、ボディ欠落)。
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        // 例外メッセージには壊れたJSONの断片(=入力値)が含まれうるため、ログには載せない。
        logInvalidInput("unreadable-body", "body");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_ERROR", "リクエストの形式が正しくありません"));
    }

    // 存在しないパスへのアクセス。catch-allに落とすと404であるべきものが500になるため個別にハンドルする。
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        // 存在しないパスへのアクセスはボットや探索行為でも起きる。1件ずつ見る価値は低いのでDEBUG。
        // 急増したかどうかはアクセスログの404件数で見る(docs/operations.md の監視項目)。
        log.debug("resource not found {}", kv("resourcePath", ex.getResourcePath()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "リソースが見つかりません"));
    }

    // 存在するパスに誤ったHTTPメソッドで来た場合(例: PATCH /api/users/7)。
    // NoResourceFoundExceptionと同じ理由で個別にハンドルする。catch-allに落とすと405であるべきものが
    // 500になり、クライアントに原因が伝わらないうえサーバーログにERRORが積もる。
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        // 正常な利用では起きない。クライアント実装の誤りかAPI変更の取りこぼしを疑うためWARN。
        log.warn("method not allowed {}", kv("method", ex.getMethod()));
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED", "このパスでは許可されていない操作です"));
    }

    // ここに落ちてくるのは「想定していなかった」バグ(NPE、DBエラー等)。
    // 原因調査は必ずサーバーログのスタックトレース(下のlog.error)から辿ること — クライアントには詳細を返さない。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "予期しないエラーが発生しました"));
    }

    // 400系(クライアントの入力ミス)の共通ログ。何が弾かれたかだけを記録し、入力値は載せない。
    private void logInvalidInput(String reason, String field) {
        log.debug("invalid request {} {}", kv("reason", reason), kv("field", field));
    }
}
