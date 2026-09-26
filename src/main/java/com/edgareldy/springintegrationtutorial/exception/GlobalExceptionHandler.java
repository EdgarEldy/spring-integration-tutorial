package com.edgareldy.springintegrationtutorial.exception;

import com.edgareldy.springintegrationtutorial.dto.ApiResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates every exception escaping a controller into an {@link ApiResponse} error, so a client
 * never has to parse a different error shape depending on what failed.
 * <p>
 * Created edgar.muhamyangabo on 9/26/26
 * Author : edgar.muhamyangabo
 * Date : 9/26/26
 * Project : spring-integration-tutorial
 */
// A @RestControllerAdvice applies to every controller. It also covers the HTTP entry points into the
// integration flows: their channels are DirectChannels, so a flow step runs in the caller's thread and
// an exception it throws travels back through the gateway into the controller, and from there here.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * @param ex the missing resource
     * @return a 404 error envelope
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * @param ex the violated business rule
     * @return a 422 error envelope
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessRuleException ex) {
        // UNPROCESSABLE_CONTENT is the RFC 9110 name of 422; Spring 7 deprecates UNPROCESSABLE_ENTITY.
        return error(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    /**
     * Bean Validation failure on a request body: the offending fields are returned as the payload.
     * A constraint declared on the whole request (a class-level rule) is reported under the name of
     * the request object, so it is never lost.
     *
     * @param ex the validation failure
     * @return a 400 envelope whose data maps each invalid field or object to its message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(globalError -> errors.putIfAbsent(globalError.getObjectName(), globalError.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(false, "Validation failed", errors, Instant.now()));
    }

    /**
     * @param ex a body that is not valid JSON or does not match the expected types
     * @return a 400 error envelope
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    /**
     * A {@code hasRole(...)} check that failed on an authenticated caller.
     *
     * @param ex the authorization failure
     * @return a 403 error envelope
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    /**
     * A caller who is not authenticated: wrong credentials at login, or a protected route called
     * without a valid token (forwarded here by the security entry point).
     *
     * @param ex the authentication failure
     * @return a 401 error envelope
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        String message;
        if (ex instanceof BadCredentialsException) {
            // Same message for an unknown email and a wrong password: the API never confirms that an
            // account exists.
            message = "Invalid email or password";
        } else if (ex instanceof AccountStatusException) {
            message = "Account is disabled or locked";
        } else {
            message = "Authentication required";
        }
        return error(HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * Anything else. Spring MVC's own exceptions (unknown route, wrong method, missing parameter...)
     * implement {@link ErrorResponse} and keep their status and headers (such as {@code Allow} on a 405);
     * only genuinely unexpected errors become a 500, without any internal detail in the body.
     *
     * @param ex the exception
     * @return an error envelope
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(ApiResponse.error(detail != null ? detail : errorResponse.getStatusCode().toString()));
        }
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private static ResponseEntity<ApiResponse<Void>> error(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }
}
