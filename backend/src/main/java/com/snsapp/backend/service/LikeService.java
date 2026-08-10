package com.snsapp.backend.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.PostSelfLikeException;
import com.snsapp.backend.mapper.LikeMapper;
import com.snsapp.backend.mapper.PostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LikeService {

    // いいねは件数が多く、1件ずつ見る価値は低い。集計対象としてDEBUGで残す。
    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

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
        log.debug("post liked {}", kv("postId", postId));
    }

    public void unlike(Long currentUserId, Long postId) {
        likeMapper.delete(postId, currentUserId);
        log.debug("post unliked {}", kv("postId", postId));
    }

    private Post requireActivePost(Long postId) {
        Post post = postMapper.findRawById(postId);
        if (post == null || post.getDeletedAt() != null) {
            throw new PostNotFoundException();
        }
        return post;
    }
}
