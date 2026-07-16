package com.snsapp.backend.mapper;

import com.snsapp.backend.dto.CommentResponse;
import com.snsapp.backend.entity.Comment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper {

    List<CommentResponse> findByPostId(@Param("postId") Long postId, @Param("currentUserId") Long currentUserId);

    CommentResponse findById(@Param("commentId") Long commentId, @Param("currentUserId") Long currentUserId);

    Comment findRawById(@Param("commentId") Long commentId);

    void insert(Comment comment);

    void updateBody(@Param("commentId") Long commentId, @Param("body") String body);

    void softDelete(@Param("commentId") Long commentId);
}
