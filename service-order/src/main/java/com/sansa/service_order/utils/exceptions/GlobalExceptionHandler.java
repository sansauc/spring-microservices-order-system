package com.sansa.service_order.utils.exceptions;

import com.sansa.service_order.utils.errors.ApiError;
import feign.FeignException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterValidationResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request){

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Order Not Found",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(apiError);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStockException(
            InsufficientStockException ex,
            HttpServletRequest request) {

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Insufficient stock to complete the requested operation",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(apiError);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, List<String>> errors = new LinkedHashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), key -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            errors.computeIfAbsent("global", key -> new ArrayList<>())
                    .add(globalError.getDefaultMessage());
        }

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Validation failed",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpServletRequest request) {

        Map<String, List<String>> errors = new LinkedHashMap<>();

        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameterName = result.getMethodParameter().getParameterName();
            if (parameterName == null) {
                parameterName = "arg" + result.getMethodParameter().getParameterIndex();
            }
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.computeIfAbsent(parameterName, key -> new ArrayList<>())
                        .add(error.getDefaultMessage());
            }
        }

        for (MessageSourceResolvable error : ex.getCrossParameterValidationResults()) {
            errors.computeIfAbsent("global", key -> new ArrayList<>())
                    .add(error.getDefaultMessage());
        }

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Validation failed",
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeignException(
            FeignException ex,
            HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.status());
        String message;

        if (status == null || status.is5xxServerError()) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Dependent service unavailable";
        } else if (status == HttpStatus.NOT_FOUND) {
            message = "Requested resource was not found in a dependent service";
        } else {
            message = "Request to a dependent service was rejected";
        }

        log.warn("Feign call failed with status {}: {}", ex.status(), ex.getMessage());

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                null
        );

        return ResponseEntity.status(status).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
            Exception ex,
            HttpServletRequest request) throws Exception {

        // Let Spring MVC's default resolvers handle the client errors they already map (400, 404, 405, 415...)
        if (ex instanceof ErrorResponse
                || ex instanceof TypeMismatchException
                || ex instanceof HttpMessageNotReadableException) {
            throw ex;
        }

        log.error("Unexpected error processing {} {}", request.getMethod(), request.getRequestURI(), ex);

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Unexpected error",
                request.getRequestURI(),
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleServiceUnavailableException(ServiceUnavailableException ex, HttpServletRequest request) {

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(apiError);

    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiError> handleRateLimitExceededException(RequestNotPermitted ex, HttpServletRequest request){

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many requests, please try again later",
                request.getRequestURI(),
                null
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "10")   // seconds; should match limitRefreshPeriod
                .body(apiError);

    }
}


