package com.snsapp.backend.mapper;

import org.apache.ibatis.annotations.Param;

import com.snsapp.backend.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RefreshTokenMapper {

    void insert(RefreshToken refreshToken);

    RefreshToken findByTokenHash(String tokenHash);

    /**
     * 正規のローテーションで失効させる。後継トークンのIDを記録する。
     *
     * <p><b>後継の有無が、並行リフレッシュの猶予を与えてよいかの判断材料になる。</b>
     */
    void revokeReplacedBy(@Param("id") Long id, @Param("replacedById") Long replacedById);

    void revoke(Long id);

    void revokeAllForUser(Long userId);
}
