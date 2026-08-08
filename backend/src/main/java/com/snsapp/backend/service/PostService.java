package com.snsapp.backend.service;

import com.snsapp.backend.dto.CreatePostRequest;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.PostImageRow;
import com.snsapp.backend.dto.PostResponse;
import com.snsapp.backend.dto.UpdatePostRequest;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.exception.InvalidFeedParameterException;
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

@Service
public class PostService {

    private static final int MAX_LIMIT = 50;
    private static final int MAX_IMAGES_PER_POST = 4;

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

    /**
     * 投稿を作成する(F-04/F-05)。
     *
     * <p>画像はブラウザからS3へ直接アップロード済みで、ここには {@code imageKeys} だけが渡る。
     * 実体の検証(存在・サイズ・形式)は {@link StorageService#promote} が行い、
     * 検証を通ったものだけが正式な場所へ移されてDBに登録される。
     */
    public PostResponse createPost(Long currentUserId, CreatePostRequest request) {
        List<String> pendingKeys = request.imageKeysOrEmpty();
        if (pendingKeys.size() > MAX_IMAGES_PER_POST) {
            throw new TooManyImagesException();
        }

        // DBに書く前に全画像を検証する。1枚でも不正なら投稿ごと失敗させ、中途半端な状態を作らない。
        List<String> imageKeys = new ArrayList<>();
        for (String pendingKey : pendingKeys) {
            imageKeys.add(storageService.promote(pendingKey, "posts"));
        }

        Post post = new Post();
        post.setUserId(currentUserId);
        post.setBody(request.body());
        postMapper.insert(post);

        for (int i = 0; i < imageKeys.size(); i++) {
            postImageMapper.insert(post.getId(), imageKeys.get(i), i);
        }

        return withImages(postMapper.findById(post.getId(), currentUserId), imageKeys);
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

    /**
     * 自分の投稿を削除する(F-16)。
     *
     * <p>投稿本体は論理削除だが、添付画像は実ファイルと post_images 行の両方を物理削除する。
     * 削除済み投稿は返信を持つ場合ツームストーンとして一覧に残り続けるため、行を残したままだと
     * 無認証で配信される /uploads/** の URL がレスポンスに乗り続けてしまう。
     */
    public void deletePost(Long currentUserId, Long postId) {
        Post raw = requireOwnedPost(currentUserId, postId);
        for (String imageUrl : imagesForPost(raw.getId())) {
            storageService.delete(imageUrl);
        }
        postImageMapper.deleteByPostId(raw.getId());
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
        return postImageMapper.findByPostIds(List.of(postId)).stream().map(PostImageRow::imageKey).toList();
    }

    /**
     * DBから取得したS3キーを表示用の署名付きURLへ変換して差し込む。
     *
     * <p>削除済み投稿(ツームストーン)は本文がSQLでNULL化されるのに合わせ、画像も必ず空で返す。
     * deletePost が行を消すため通常は空だが、この修正より前に削除された投稿の行が残っていても漏らさない。
     */
    private PostResponse withImages(PostResponse post, List<String> imageKeys) {
        List<String> imageUrls = post.deleted()
                ? List.of()
                : imageKeys.stream().map(storageService::presignedGetUrl).toList();
        // authorAvatarUrl にはSQL由来のS3キーが入っているため、あわせて変換する
        return post.withImageUrls(storageService.presignedGetUrl(post.authorAvatarUrl()), imageUrls);
    }

    // 一覧系(listFeed)向け: N+1を避けるため対象postId群の画像を1クエリでまとめて取得し、post_idごとにグルーピングして差し込む。
    private List<PostResponse> withImages(List<PostResponse> posts) {
        if (posts.isEmpty()) {
            return posts;
        }
        List<Long> postIds = posts.stream().map(PostResponse::id).toList();
        Map<Long, List<String>> keysByPostId = postImageMapper.findByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(
                        PostImageRow::postId, Collectors.mapping(PostImageRow::imageKey, Collectors.toList())));
        return posts.stream()
                .map(post -> withImages(post, keysByPostId.getOrDefault(post.id(), List.of())))
                .toList();
    }
}
