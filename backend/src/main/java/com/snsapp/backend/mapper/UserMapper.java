package com.snsapp.backend.mapper;

import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    long countUsers();

    User findByEmail(String email);

    User findById(Long id);

    void insert(User user);

    ProfileResponse findProfileById(@Param("userId") Long userId, @Param("currentUserId") Long currentUserId);

    void update(
            @Param("id") Long id,
            @Param("displayName") String displayName,
            @Param("bio") String bio,
            @Param("avatarUrl") String avatarUrl);

    List<UserSummaryResponse> searchByDisplayName(
            @Param("currentUserId") Long currentUserId,
            @Param("query") String query,
            @Param("cursor") Long cursor,
            @Param("limit") int limit);
}
