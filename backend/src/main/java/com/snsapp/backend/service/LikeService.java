package com.snsapp.backend.service;

import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.PostSelfLikeException;
import com.snsapp.backend.mapper.LikeMapper;
import com.snsapp.backend.mapper.PostMapper;
import org.springframework.stereotype.Service;

@Service
public class LikeService {

    private final LikeMapper likeMapper;
    private final PostMapper postMapper;

    public LikeService(LikeMapper likeMapper, PostMapper postMapper) {
        this.likeMapper = likeMapper;
        this.postMapper = postMapper;
    }

    public void like(Long currentUserId, Long postId) {
        Post post = requireActivePost(postId);
        if (post.getUserId().equals(currentUserId)) {
            throw new PostSelfLikeException();
        }
        likeMapper.insertIgnoreDuplicate(postId, currentUserId);
    }

    public void unlike(Long currentUserId, Long postId) {
        likeMapper.delete(postId, currentUserId);
    }

    private Post requireActivePost(Long postId) {
        Post post = postMapper.findRawById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new PostNotFoundException();
        }
        return post;
    }
}
