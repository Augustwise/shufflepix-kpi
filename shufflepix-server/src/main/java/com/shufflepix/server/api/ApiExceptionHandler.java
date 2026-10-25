package com.shufflepix.server.api;

import com.shufflepix.common.dto.ErrorResponse;
import com.shufflepix.server.service.InvalidRequestException;
import com.shufflepix.server.service.NameConflictException;
import com.shufflepix.server.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> invalid(InvalidRequestException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> unreadable(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Некоректний формат запиту");
    }

    @ExceptionHandler(NameConflictException.class)
    public ResponseEntity<ErrorResponse> nameConflict(NameConflictException e) {
        return error(HttpStatus.CONFLICT, "NAME_CONFLICT", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> versionConflict(OptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "Проєкт змінено з іншого робочого місця. Збережіть свою версію як новий проєкт.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException e) {
        log.warn("Порушення цілісності даних", e);
        return error(HttpStatus.CONFLICT, "DATA_INTEGRITY", "Дані не відповідають обмеженням бази даних");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", "Обсяг запиту перевищує допустимий");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), code, message));
    }
}
