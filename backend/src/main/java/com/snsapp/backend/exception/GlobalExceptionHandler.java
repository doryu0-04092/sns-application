package com.snsapp.backend.exception;

import com.snsapp.backend.common.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 想定内エラー。各ApiExceptionサブクラスが持つ code/message/status をそのままレスポンスへ反映する。
    // 原因の特定は例外クラス自体(exception/*NotFoundException等、各クラスにスロー元をコメント済み)を見ればよい。
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
    }

    // 必須クエリパラメータの欠落。Bean Validationと違いSpringが引数解決の時点で投げるため個別にハンドルする。
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = "必須パラメータ「%s」が指定されていません".formatted(ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
    }

    // パス変数/クエリパラメータの型不一致(例: Long のはずの {postId} に "abc" が来た)。
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "パラメータ「%s」の形式が正しくありません".formatted(ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.of("VALIDATION_ERROR", message));
    }

    // リクエストボディが読み取れない(壊れたJSON、Content-Type不一致、ボディ欠落)。
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDATION_ERROR", "リクエストの形式が正しくありません"));
    }

    // 存在しないパスへのアクセス。catch-allに落とすと404であるべきものが500になるため個別にハンドルする。
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("NOT_FOUND", "リソースが見つかりません"));
    }

    // multipartのファイルサイズ上限(spring.servlet.multipart.max-file-size/max-request-size)超過。
    // コントローラーに到達する前にSpring側で投げられるためApiExceptionを継承できず、個別にハンドルする。
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("FILE_TOO_LARGE", "アップロード可能なファイルサイズを超えています"));
    }

    // ここに落ちてくるのは「想定していなかった」バグ(NPE、DBエラー等)。
    // 原因調査は必ずサーバーログのスタックトレース(下のlog.error)から辿ること — クライアントには詳細を返さない。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "予期しないエラーが発生しました"));
    }
}
