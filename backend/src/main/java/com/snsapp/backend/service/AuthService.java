package com.snsapp.backend.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.dto.LoginRequest;
import com.snsapp.backend.dto.SignupRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.DuplicateEmailException;
import com.snsapp.backend.exception.InvalidCredentialsException;
import com.snsapp.backend.exception.UnauthenticatedException;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    // 業務上の節目だけをINFOで残す。「登録はできたのにログインできない」のような問い合わせで、
    // どこまで成功したかを切り分けるために使う。識別子(userId)のみで、
    // メールアドレス・パスワードなど本人を特定できる値やクレデンシャルは載せない。
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, StorageService storageService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
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

        log.info("user signed up {}", kv("userId", user.getId()));
        return UserResponse.from(user, storageService.viewUrl(user.getAvatarKey()));
    }

    public UserResponse login(LoginRequest request) {
        User user = userMapper.findByEmail(request.email());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        log.info("user logged in {}", kv("userId", user.getId()));
        return UserResponse.from(user, storageService.viewUrl(user.getAvatarKey()));
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new UnauthenticatedException();
        }
        return UserResponse.from(user, storageService.viewUrl(user.getAvatarKey()));
    }
}
