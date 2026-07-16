package com.snsapp.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FollowMapper {

    void insertIgnoreDuplicate(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    void delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);
}
