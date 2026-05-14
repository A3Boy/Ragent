package com.jawn.ragent.controller;

import com.jawn.ragent.dto.auth.AuthResponse;
import com.jawn.ragent.dto.auth.LoginRequest;
import com.jawn.ragent.dto.auth.RegisterRequest;
import com.jawn.ragent.entity.User;
import com.jawn.ragent.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("用户名不能为空"));
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            return ResponseEntity.badRequest().body(errorResponse("密码至少 6 位"));
        }

        try {
            String token = authService.register(request.getUsername(), request.getEmail(), request.getPassword());
            String userId = authService.extractUserId(token);
            return ResponseEntity.ok(new AuthResponse(token, userId, request.getUsername(), request.getEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("用户名不能为空"));
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body(errorResponse("密码不能为空"));
        }

        try {
            String token = authService.login(request.getUsername(), request.getPassword());
            String userId = authService.extractUserId(token);
            User user = authService.getUserById(userId);
            return ResponseEntity.ok(new AuthResponse(token, userId, user.getUsername(), user.getEmail()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestAttribute("userId") String userId) {
        User user = authService.getUserById(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        response.put("createdAt", user.getCreatedAt());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}
