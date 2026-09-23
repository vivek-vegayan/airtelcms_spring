package com.vegayan.airtelmanagement.common.exception;

import lombok.Getter;

@Getter
public class StageActionBlockedException extends BusinessException {

    private final String code;
    private final String hint;
    private final String stage;
    private final String crqNo;
    private final int httpStatus;

    public StageActionBlockedException(String code,
                                       String message,
                                       String hint,
                                       String stage,
                                       String crqNo,
                                       int httpStatus) {
        super(message);
        this.code = code;
        this.hint = hint;
        this.stage = stage;
        this.crqNo = crqNo;
        this.httpStatus = httpStatus;
    }

}
