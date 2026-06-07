package com.shop.clothingstore.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.shop.clothingstore.dto.api.ApiErrorResponse;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Handles errors forwarded to /error by the servlet container — including
 * MaxUploadSizeExceededException which is thrown inside DispatcherServlet
 * before any @ExceptionHandler or filter can intercept it.
 *
 * Only handles API paths (/api/**) with a JSON body. All other paths fall
 * through to Spring Boot's default Whitelabel error page.
 */
@RestController
public class GlobalErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiErrorResponse> handleError(HttpServletRequest request) {

        Throwable cause = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);

        // Unwrap servlet wrapper if needed
        if (cause == null) {
            Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
            int status = statusCode instanceof Integer s ? s : 500;
            String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
            if (path == null) path = request.getRequestURI();
            return ResponseEntity.status(status).body(
                    new ApiErrorResponse(status, "HTTP_" + status, "An error occurred", path));
        }

        Throwable root = cause;
        while (root.getCause() != null && !(root instanceof MaxUploadSizeExceededException)) {
            root = root.getCause();
        }

        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path == null) path = request.getRequestURI();

        if (root instanceof MaxUploadSizeExceededException) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                    new ApiErrorResponse(413, "FILE_TOO_LARGE",
                            "Upload exceeds the allowed size limit (max 20 MB per file, 50 MB per request)",
                            path));
        }

        int status = resolveStatus(request);
        return ResponseEntity.status(status).body(
                new ApiErrorResponse(status, "HTTP_" + status, cause.getMessage(), path));
    }

    private int resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return code instanceof Integer s ? s : 500;
    }
}
