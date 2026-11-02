package com.chagokchagok.server.global.exception;

import com.chagokchagok.server.global.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 애플리케이션에서 명시적으로 발생시킨 비즈니스 예외
     */
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(
        GeneralException exception
    ) {
        BaseErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ApiResponse.failure(
                    errorCode.getCode(),
                    errorCode.getMessage()
                )
            );
    }

    /**
     * @Valid가 적용된 RequestBody DTO 검증 실패
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        List<ValidationError> errors = exception
            .getBindingResult()
            .getAllErrors()
            .stream()
            .map(error ->
                new ValidationError(
                    error instanceof FieldError fieldError
                        ? fieldError.getField()
                        : "request",
                    error.getDefaultMessage()
                )
            )
            .toList();

        return createValidationErrorResponse(errors);
    }

    /**
     * 메서드 파라미터 Validation 실패
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<?> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception
    ) {
        if (exception.isForReturnValue()) {
            log.error("Controller return value validation failed", exception);

            return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Stream<ValidationError> parameterErrors = exception
            .getParameterValidationResults()
            .stream()
            .flatMap(result -> {
                String parameterName = result
                    .getMethodParameter()
                    .getParameterName();

                if (parameterName == null) {
                    parameterName = "arg" + result
                        .getMethodParameter()
                        .getParameterIndex();
                }

                String fallbackField = parameterName;

                return result
                    .getResolvableErrors()
                    .stream()
                    .map(error ->
                        new ValidationError(
                            error instanceof FieldError fieldError
                                ? fieldError.getField()
                                : fallbackField,
                            error.getDefaultMessage()
                        )
                    );
            });

        Stream<ValidationError> crossParameterErrors = exception
            .getCrossParameterValidationResults()
            .stream()
            .map(error -> new ValidationError("request", error.getDefaultMessage()));

        List<ValidationError> errors = Stream
            .concat(parameterErrors, crossParameterErrors)
            .toList();

        return createValidationErrorResponse(errors);
    }

    /**
     * @RequestParam, @PathVariable 등에 대한 Constraint Validation 실패
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleConstraintViolationException(
        ConstraintViolationException exception
    ) {
        List<ValidationError> errors = exception
            .getConstraintViolations()
            .stream()
            .map(violation ->
                new ValidationError(
                    violation.getPropertyPath().toString(),
                    violation.getMessage()
                )
            )
            .toList();

        return createValidationErrorResponse(errors);
    }

    /**
     * JSON 문법 오류, Enum 변환 실패 등 RequestBody 파싱 실패
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
        HttpMessageNotReadableException exception
    ) {
        return createErrorResponse(ErrorCode.INVALID_REQUEST_BODY);
    }

    /**
     * 필수 Request Parameter 누락
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(
        MissingServletRequestParameterException exception
    ) {
        return createErrorResponse(ErrorCode.MISSING_REQUEST_PARAMETER);
    }

    /**
     * Request Parameter 또는 PathVariable 타입 변환 실패
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
        MethodArgumentTypeMismatchException exception
    ) {
        return createErrorResponse(ErrorCode.TYPE_MISMATCH);
    }

    /**
     * 지원하지 않는 HTTP Method 요청
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(
        HttpRequestMethodNotSupportedException exception
    ) {
        return createErrorResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 지원하지 않는 Content-Type 요청
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupportedException(
        HttpMediaTypeNotSupportedException exception
    ) {
        return createErrorResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * 존재하지 않는 정적 Resource 요청
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(
        NoResourceFoundException exception
    ) {
        return createErrorResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 데이터베이스 무결성 제약 조건 위반
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
        DataIntegrityViolationException exception
    ) {
        log.warn("Data integrity violation", exception);

        return createErrorResponse(ErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    /**
     * 처리되지 않은 모든 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
        Exception exception
    ) {
        log.error("Unhandled exception", exception);

        return createErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> createErrorResponse(
        BaseErrorCode errorCode
    ) {
        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ApiResponse.failure(
                    errorCode.getCode(),
                    errorCode.getMessage()
                )
            );
    }

    private ResponseEntity<ApiResponse<List<ValidationError>>> createValidationErrorResponse(
        List<ValidationError> errors
    ) {
        ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

        return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ApiResponse.failure(
                    errorCode.getCode(),
                    errorCode.getMessage(),
                    errors
                )
            );
    }
}
