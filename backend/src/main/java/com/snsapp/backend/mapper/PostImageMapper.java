package com.snsapp.backend.mapper;

import com.snsapp.backend.dto.PostImageRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostImageMapper {

    List<PostImageRow> findByPostIds(@Param("postIds") List<Long> postIds);

    void insert(@Param("postId") Long postId, @Param("imageKey") String imageKey, @Param("displayOrder") int displayOrder);

    void deleteByPostId(@Param("postId") Long postId);
}
