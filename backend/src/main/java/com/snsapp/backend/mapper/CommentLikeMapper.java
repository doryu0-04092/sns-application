package com.snsapp.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentLikeMapper {

    void insertIgnoreDuplicate(@Param("commentId") Long commentId, @Param("userId") Long userId);

    void delete(@Param("commentId") Long commentId, @Param("userId") Long userId);
}
