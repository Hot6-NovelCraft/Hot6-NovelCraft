package com.example.hot6novelcraft.common.exception;

import com.example.hot6novelcraft.common.dto.BaseResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ServiceErrorException.class)
    public ResponseEntity<BaseResponse<Void>> handleServiceErrorException(ServiceErrorException e) {
        log.error(e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatus()).body(BaseResponse.fail(e.getHttpStatus().name(), e.getMessage()));
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Void>> IllegalArgumentExceptionHandler(IllegalArgumentException e) {
        log.error("요청 값 유효성 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "요청 값이 유효하지 않습니다"));
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> MethodArgumentNotValidExceptionHandler(MethodArgumentNotValidException e) {
        log.error("데이터 유효성 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), e.getAllErrors().get(0).getDefaultMessage()));
    }

    @ExceptionHandler(value = HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> HttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("데이터 JSON 변환 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "데이터 처리에 문제가 발생하였습니다"));
    }

    @ExceptionHandler(value = DataIntegrityViolationException.class)
    public ResponseEntity<BaseResponse<Void>> DataIntegrityViolationExceptionHandler(DataIntegrityViolationException e) {
        log.error("데이터 저장 실패 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "데이터 저장에 실패하였습니다"));
    }

    @ExceptionHandler(value = NoResourceFoundException.class)
    public ResponseEntity<BaseResponse<Void>> NoResourceFoundExceptionHandler(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(BaseResponse.fail(HttpStatus.NOT_FOUND.name(), "주소를 다시 한번 확인해주세요"));
    }

    @ExceptionHandler(value = HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<BaseResponse<Void>> HttpRequestMethodNotSupportedExceptionHandler(HttpRequestMethodNotSupportedException e) {
        log.error("지원하지 않는 HTTP 메서드 : ", e);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(BaseResponse.fail(HttpStatus.METHOD_NOT_ALLOWED.name(), "지원하지 않는 HTTP 메서드입니다."));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<?> handleAuthenticationCredentialsNotFoundException(AuthenticationCredentialsNotFoundException e) {
        log.error("인증 정보 없음: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN) // 403
                .body(BaseResponse.fail("403", "접근 권한이 없습니다"));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAuthorizationDeniedException(
            AuthorizationDeniedException e) {
        log.error("권한 없음: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(BaseResponse.fail(HttpStatus.FORBIDDEN.name(), "접근 권한이 없습니다"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.error("AccessDeniedException 발생: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(BaseResponse.fail(HttpStatus.FORBIDDEN.name(), "접근 권한이 없습니다."));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleEntityNotFoundException(jakarta.persistence.EntityNotFoundException e) {
        log.error("EntityNotFoundException 발생: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.fail(HttpStatus.NOT_FOUND.name(), e.getMessage()));
    }

    @ExceptionHandler(value = MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> MissingServletRequestParameterExceptionHandler(MissingServletRequestParameterException e) {
        log.error("필수 파라미터 누락 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "필수 파라미터가 누락되었습니다"));
    }

    @ExceptionHandler(value = ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Void>> ConstraintViolationExceptionHandler(ConstraintViolationException e) {
        log.error("요청 파라미터 유효성 에러 발생 : ", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.fail(HttpStatus.BAD_REQUEST.name(), "요청 파라미터가 유효하지 않습니다"));
    }

    @ExceptionHandler(value = WithdrawalPendingException.class)
    public ResponseEntity<BaseResponse<Object>> handleWithdrawalPendingException(WithdrawalPendingException e) {
        log.warn("탈퇴 유예 유저 로그인 시도: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(BaseResponse.fail(HttpStatus.CONFLICT.name(), "탈퇴 유예 유저가 로그인을 시도했습니다", e.getRecoveryToken()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleCriticalErrorException(Exception e) {
        log.error("서버 에러 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BaseResponse.fail(HttpStatus.INTERNAL_SERVER_ERROR.name(), "서버 오류로 인해 잠시 후 다시 시도하시기 바랍니다"));
    }
}
