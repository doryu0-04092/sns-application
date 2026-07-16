package com.snsapp.backend.mapper;

import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.entity.Post;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostMapper {

    List<PostResponse> findFeedAll(
            @Param("currentUserId") Long currentUserId,
            @Param("cursor") Long cursor,
            @Param("sinceId") Long sinceId,
            @Param("limit") int limit);

    List<PostResponse> findFeedFollowing(
            @Param("currentUserId") Long currentUserId,
            @Param("cursor") Long cursor,
            @Param("sinceId") Long sinceId,
            @Param("limit") int limit);

    PostResponse findById(@Param("postId") Long postId, @Param("currentUserId") Long currentUserId);

    Post findRawById(@Param("postId") Long postId);

    void insert(Post post);

    void updateBody(@Param("postId") Long postId, @Param("body") String body);

    void softDelete(@Param("postId") Long postId);
}
