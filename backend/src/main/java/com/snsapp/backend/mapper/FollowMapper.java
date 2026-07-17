package com.snsapp.backend.mapper;

import com.snsapp.backend.dto.UserSummaryResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FollowMapper {

    void insertIgnoreDuplicate(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    void delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    List<UserSummaryResponse> findFollowers(
            @Param("targetUserId") Long targetUserId,
            @Param("currentUserId") Long currentUserId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);

    List<UserSummaryResponse> findFollowing(
            @Param("targetUserId") Long targetUserId,
            @Param("currentUserId") Long currentUserId,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);
}
