package com.shop.clothingstore.filter;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.clothingstore.dto.api.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Catches MaxUploadSizeExceededException thrown by Tomcat/Spring multipart resolver
 * before it reaches DispatcherServlet, so @ExceptionHandler cannot intercept it.
 * Returns a structured JSON 413 response instead of a 500.
 */
@Component
@Order(1)
public class MultipartSizeLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public MultipartSizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (MaxUploadSizeExceededException ex) {
            sendError(response, request.getRequestURI());
        } catch (ServletException ex) {
            if (ex.getCause() instanceof MaxUploadSizeExceededException) {
                sendError(response, request.getRequestURI());
            } else {
                throw ex;
            }
        }
    }

    private void sendError(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiErrorResponse body = new ApiErrorResponse(
                413,
                "FILE_TOO_LARGE",
                "Upload exceeds the allowed size limit (max 20 MB per file, 50 MB per request)",
                path
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
