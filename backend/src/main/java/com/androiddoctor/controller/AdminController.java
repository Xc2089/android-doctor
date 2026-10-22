package com.androiddoctor.controller;

import com.androiddoctor.dto.AdminLoginRequest;
import com.androiddoctor.dto.AdminStatusResponse;
import com.androiddoctor.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminAuthService authService;

    public AdminController(AdminAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/status")
    public AdminStatusResponse status(HttpServletRequest request) {
        return new AdminStatusResponse(authService.isAuthorized(request),
                authService.passwordSource(), authService.passwordHint());
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody AdminLoginRequest body,
                                        HttpServletRequest request, HttpServletResponse response) {
        AdminAuthService.LoginResult result = authService.login(body.password(), request.getRemoteAddr());
        if (result.rateLimited()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "登录尝试过多，请稍后再试"));
        }
        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "管理员密码错误"));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(result.token(), request, authService.sessionSeconds()).toString());
        return ResponseEntity.ok(new AdminStatusResponse(true, authService.passwordSource(), authService.passwordHint()));
    }

    @PostMapping("/logout")
    public AdminStatusResponse logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request);
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie("", request, 0).toString());
        return new AdminStatusResponse(false, authService.passwordSource(), authService.passwordHint());
    }

    private ResponseCookie sessionCookie(String value, HttpServletRequest request, long maxAge) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(AdminAuthService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}
