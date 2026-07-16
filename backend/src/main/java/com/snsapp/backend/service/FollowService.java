package com.snsapp.backend.service;

import com.snsapp.backend.exception.SelfFollowException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.FollowMapper;
import com.snsapp.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    public FollowService(FollowMapper followMapper, UserMapper userMapper) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
    }

    public void follow(Long currentUserId, Long targetUserId) {
        if (targetUserId.equals(currentUserId)) {
            throw new SelfFollowException();
        }
        if (userMapper.findById(targetUserId) == null) {
            throw new UserNotFoundException();
        }
        followMapper.insertIgnoreDuplicate(currentUserId, targetUserId);
    }

    public void unfollow(Long currentUserId, Long targetUserId) {
        followMapper.delete(currentUserId, targetUserId);
    }
}
