package com.snsapp.backend.controller;

import com.snsapp.backend.mapper.UserMapper;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final UserMapper userMapper;

    public HealthController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        long userCount = userMapper.countUsers();
        return Map.of("data", Map.of("status", "ok", "userCount", userCount));
    }
}
