package com.shop.clothingstore.exception;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.clothingstore.dto.api.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Intercepts MaxUploadSizeExceededException before DefaultHandlerExceptionResolver.
 *
 * Spring throws this exception inside DispatcherServlet.checkMultipart() — before
 * any handler is resolved — so @ExceptionHandler and @ControllerAdvice never fire.
 * A HandlerExceptionResolver at HIGHEST_PRECEDENCE is the only reliable intercept point.
 *
 * - API requests  (/api/**)  → JSON 413 body
 * - Admin/web requests       → redirect back with flash-style query param
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MultipartSizeExceptionResolver implements HandlerExceptionResolver {

    private static final String ERROR_MSG =
            "Upload exceeds the allowed size limit (max 20 MB per file, 50 MB per request)";

    private final ObjectMapper objectMapper;

    public MultipartSizeExceptionResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception ex) {

        if (!isMaxUploadSize(ex)) {
            return null;
        }

        String uri = request.getRequestURI();

        try {
            if (uri.startsWith("/api/")) {
                writeJson(response, uri);
            } else {
                redirectWithError(request, response);
            }
        } catch (IOException ignored) {
        }

        return new ModelAndView();
    }

    private boolean isMaxUploadSize(Exception ex) {
        if (ex instanceof MaxUploadSizeExceededException) return true;
        Throwable cause = ex.getCause();
        return cause instanceof MaxUploadSizeExceededException;
    }

    private void writeJson(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                new ApiErrorResponse(413, "FILE_TOO_LARGE", ERROR_MSG, path));
    }

    private void redirectWithError(HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        String referer = request.getHeader("Referer");
        String back = (referer != null && !referer.isBlank()) ? referer : "/admin/products";
        // Append error as query param — flash attributes require a redirect cycle
        // through Spring MVC which is unavailable at this resolver level.
        String separator = back.contains("?") ? "&" : "?";
        response.sendRedirect(back + separator + "uploadError=size");
    }
}
