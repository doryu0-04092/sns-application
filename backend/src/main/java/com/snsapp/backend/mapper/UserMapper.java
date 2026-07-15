package com.snsapp.backend.mapper;

import com.snsapp.backend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    long countUsers();

    User findByEmail(String email);

    User findById(Long id);

    void insert(User user);
}
