package com.snsapp.backend.mapper;

import com.snsapp.backend.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper {

    void insert(RefreshToken refreshToken);

    RefreshToken findByTokenHash(String tokenHash);

    void revoke(Long id);

    void revokeAllForUser(Long userId);
}
