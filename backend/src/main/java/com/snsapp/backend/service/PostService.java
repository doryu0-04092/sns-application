package com.snsapp.backend.service;

import com.snsapp.backend.dto.CreatePostRequest;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.InvalidFeedParameterException;
import com.snsapp.backend.exception.PostForbiddenException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.mapper.PostMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PostService {

    private static final int MAX_LIMIT = 50;

    private final PostMapper postMapper;

    public PostService(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    public CursorPage<PostResponse> listFeed(
            Long currentUserId, String feed, Long cursor, Long sinceId, int limit, Long authorId) {
        if (!"all".equals(feed) && !"following".equals(feed)) {
            throw new InvalidFeedParameterException();
        }

        int clampedLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<PostResponse> rows = authorId != null
                ? postMapper.findByAuthor(currentUserId, authorId, cursor, sinceId, clampedLimit + 1)
                : "following".equals(feed)
                        ? postMapper.findFeedFollowing(currentUserId, cursor, sinceId, clampedLimit + 1)
                        : postMapper.findFeedAll(currentUserId, cursor, sinceId, clampedLimit + 1);

        boolean hasMore = rows.size() > clampedLimit;
        List<PostResponse> items = hasMore ? rows.subList(0, clampedLimit) : rows;
        String nextCursor = hasMore ? String.valueOf(items.get(items.size() - 1).id()) : null;
        return new CursorPage<>(items, nextCursor);
    }

    public PostResponse createPost(Long currentUserId, CreatePostRequest request) {
        Post post = new Post();
        post.setUserId(currentUserId);
        post.setBody(request.body());
        postMapper.insert(post);
        return postMapper.findById(post.getId(), currentUserId);
    }

    public PostResponse getPost(Long currentUserId, Long postId) {
        PostResponse post = postMapper.findById(postId, currentUserId);
        if (post == null) {
            throw new PostNotFoundException();
        }
        return post;
    }

    public PostResponse updatePost(Long currentUserId, Long postId, UpdatePostRequest request) {
        Post raw = requireOwnedPost(currentUserId, postId);
        postMapper.updateBody(raw.getId(), request.body());
        return postMapper.findById(postId, currentUserId);
    }

    public void deletePost(Long currentUserId, Long postId) {
        Post raw = requireOwnedPost(currentUserId, postId);
        postMapper.softDelete(raw.getId());
    }

    // 更新・削除の前段チェック。存在しない/既に削除済み -> 404、他人の投稿 -> 403 で区別する。
    private Post requireOwnedPost(Long currentUserId, Long postId) {
        Post raw = postMapper.findRawById(postId);
        if (raw == null || raw.getDeletedAt() != null) {
            throw new PostNotFoundException();
        }
        if (!raw.getUserId().equals(currentUserId)) {
            throw new PostForbiddenException();
        }
        return raw;
    }
}
