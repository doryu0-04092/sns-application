package com.snsapp.backend.service;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostImageRow;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.InvalidFeedParameterException;
import com.snsapp.backend.exception.InvalidPostBodyException;
import com.snsapp.backend.exception.PostForbiddenException;
import com.snsapp.backend.exception.PostNotFoundException;
import com.snsapp.backend.exception.TooManyImagesException;
import com.snsapp.backend.mapper.PostImageMapper;
import com.snsapp.backend.mapper.PostMapper;
import com.snsapp.backend.storage.StorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PostService {

    private static final int MAX_LIMIT = 50;
    private static final int MAX_IMAGES_PER_POST = 4;
    private static final int MAX_BODY_LENGTH = 280;

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final StorageService storageService;

    public PostService(PostMapper postMapper, PostImageMapper postImageMapper, StorageService storageService) {
        this.postMapper = postMapper;
        this.postImageMapper = postImageMapper;
        this.storageService = storageService;
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
        items = withImages(items);
        String nextCursor = hasMore ? String.valueOf(items.get(items.size() - 1).id()) : null;
        return new CursorPage<>(items, nextCursor);
    }

    public PostResponse createPost(Long currentUserId, String body, List<MultipartFile> images) {
        if (body == null || body.isBlank() || body.length() > MAX_BODY_LENGTH) {
            throw new InvalidPostBodyException();
        }
        if (images.size() > MAX_IMAGES_PER_POST) {
            throw new TooManyImagesException();
        }

        Post post = new Post();
        post.setUserId(currentUserId);
        post.setBody(body);
        postMapper.insert(post);

        List<String> imageUrls = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            String imageUrl = storageService.store(images.get(i), "posts");
            postImageMapper.insert(post.getId(), imageUrl, i);
            imageUrls.add(imageUrl);
        }

        return withImages(postMapper.findById(post.getId(), currentUserId), imageUrls);
    }

    public PostResponse getPost(Long currentUserId, Long postId) {
        PostResponse post = postMapper.findById(postId, currentUserId);
        if (post == null) {
            throw new PostNotFoundException();
        }
        return withImages(post, imagesForPost(postId));
    }

    public PostResponse updatePost(Long currentUserId, Long postId, UpdatePostRequest request) {
        Post raw = requireOwnedPost(currentUserId, postId);
        postMapper.updateBody(raw.getId(), request.body());
        return withImages(postMapper.findById(postId, currentUserId), imagesForPost(postId));
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

    private List<String> imagesForPost(Long postId) {
        return postImageMapper.findByPostIds(List.of(postId)).stream().map(PostImageRow::imageUrl).toList();
    }

    private PostResponse withImages(PostResponse post, List<String> imageUrls) {
        return new PostResponse(post.id(), post.body(), post.authorId(), post.authorDisplayName(),
                post.authorAvatarUrl(), post.createdAt(), post.updatedAt(), post.commentCount(),
                post.likeCount(), post.isMine(), post.isFollowing(), post.isLiked(), post.deleted(), imageUrls);
    }

    // 一覧系(listFeed)向け: N+1を避けるため対象postId群の画像を1クエリでまとめて取得し、post_idごとにグルーピングして差し込む。
    private List<PostResponse> withImages(List<PostResponse> posts) {
        if (posts.isEmpty()) {
            return posts;
        }
        List<Long> postIds = posts.stream().map(PostResponse::id).toList();
        Map<Long, List<String>> imagesByPostId = postImageMapper.findByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(
                        PostImageRow::postId, Collectors.mapping(PostImageRow::imageUrl, Collectors.toList())));
        return posts.stream()
                .map(post -> withImages(post, imagesByPostId.getOrDefault(post.id(), List.of())))
                .toList();
    }
}
