package com.snsapp.backend.exception;

import org.springframework.http.HttpStatus;

// 想定内(クライアントに理由を伝えるべき)エラーの共通基底クラス。
// 新しい失敗ケースを追加するときは、既存クラスを使い回さず用途ごとにサブクラスを作ること
// (code/messageでフロント・ログの両方から原因を一意に特定できるようにするため)。
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
