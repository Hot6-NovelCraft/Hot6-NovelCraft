package com.example.hot6novelcraft.common.exception;

import com.example.hot6novelcraft.common.exception.domain.UserExceptionEnum;
import lombok.Getter;

@Getter
public class WithdrawalPendingException extends RuntimeException {
    private final UserExceptionEnum exceptionEnum;
    private final String recoveryToken;

    public WithdrawalPendingException(UserExceptionEnum exceptionEnum, String recoveryToken) {
        super(exceptionEnum.getMessage());
        this.exceptionEnum = exceptionEnum;
        this.recoveryToken = recoveryToken;
    }
}
