package com.snsapp.backend.exception;

import com.snsapp.backend.common.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    // ここに落ちてくるのは「想定していなかった」バグ(NPE、DBエラー等)。
    // 原因調査は必ずサーバーログのスタックトレース(下のlog.error)から辿ること — クライアントには詳細を返さない。
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "予期しないエラーが発生しました"));
    }
}
