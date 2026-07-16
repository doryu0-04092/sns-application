package com.snsapp.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LikeMapper {

    void insertIgnoreDuplicate(@Param("postId") Long postId, @Param("userId") Long userId);

    void delete(@Param("postId") Long postId, @Param("userId") Long userId);
}
