package com.example.dialogueapi.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiTokenInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-API-TOKEN";

    private final AppProperties properties;

    public ApiTokenInterceptor(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String configuredToken = properties.getSecurity().getToken();
        if (!StringUtils.hasText(configuredToken)) {
            return true;
        }

        String requestToken = request.getHeader(HEADER_NAME);
        if (configuredToken.equals(requestToken)) {
            return true;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"error\":\"Unauthorized\"}");
        return false;
    }
}
