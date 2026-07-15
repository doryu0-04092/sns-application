package com.snsapp.backend.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    long countUsers();
}
