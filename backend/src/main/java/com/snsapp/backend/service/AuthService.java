package com.snsapp.backend.service;

import com.snsapp.backend.dto.LoginRequest;
import com.snsapp.backend.dto.SignupRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.DuplicateEmailException;
import com.snsapp.backend.exception.InvalidCredentialsException;
import com.snsapp.backend.exception.UnauthenticatedException;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponse signup(SignupRequest request) {
        if (userMapper.findByEmail(request.email()) != null) {
            throw new DuplicateEmailException();
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        userMapper.insert(user);

        return UserResponse.from(user);
    }

    public LoginResult login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.issueToken(user.getId());
        return new LoginResult(UserResponse.from(user), token);
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new UnauthenticatedException();
        }
        return UserResponse.from(user);
    }

    public record LoginResult(UserResponse user, String token) {
    }
}
