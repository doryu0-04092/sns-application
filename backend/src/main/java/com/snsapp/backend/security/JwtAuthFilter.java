package com.snsapp.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snsapp.backend.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CURRENT_USER_ID_ATTRIBUTE = "currentUserId";
    private static final String AUTH_COOKIE_NAME = "auth_token";
    private static final Set<String> PUBLIC_PATHS =
            Set.of("/api/auth/signup", "/api/auth/login", "/api/health");

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_PATHS.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        Long userId = token == null ? null : tryParseUserId(token);
        if (userId == null) {
            writeUnauthenticated(response);
            return;
        }

        request.setAttribute(CURRENT_USER_ID_ATTRIBUTE, userId);
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AUTH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Long tryParseUserId(String token) {
        try {
            return jwtService.parseUserId(token);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void writeUnauthenticated(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of("UNAUTHENTICATED", "認証が必要です");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
