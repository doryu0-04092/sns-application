package com.snsapp.backend.support;

import com.snsapp.backend.entity.Comment;
import com.snsapp.backend.entity.Post;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.mapper.CommentMapper;
import com.snsapp.backend.mapper.PostImageMapper;
import com.snsapp.backend.mapper.PostMapper;
import com.snsapp.backend.mapper.UserMapper;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * テスト用のデータ投入ヘルパー。
 *
 * <p>各テストは{@code @Transactional}でロールバックされるが、emailはUNIQUE制約があるため
 * 連番で必ず一意になるようにしている(ロールバックされてもシーケンスは戻らないため、
 * 連番だけでは同一JVM内の衝突は防げても十分)。
 */
@Component
public class TestFixtures {

    private static final AtomicLong SEQ = new AtomicLong();

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final PostImageMapper postImageMapper;

    public TestFixtures(
            UserMapper userMapper,
            PostMapper postMapper,
            CommentMapper commentMapper,
            PostImageMapper postImageMapper) {
        this.userMapper = userMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.postImageMapper = postImageMapper;
    }

    public User user() {
        return user("テストユーザー");
    }

    public User user(String displayName) {
        long seq = SEQ.incrementAndGet();
        User user = new User();
        user.setEmail("fixture-%d-%d@example.com".formatted(System.nanoTime(), seq));
        user.setPasswordHash("$2a$10$dummydummydummydummydummydummydummydummydummydummydu");
        user.setDisplayName(displayName);
        userMapper.insert(user);
        return user;
    }

    public Post post(User author) {
        return post(author, "テスト投稿");
    }

    public Post post(User author, String body) {
        Post post = new Post();
        post.setUserId(author.getId());
        post.setBody(body);
        postMapper.insert(post);
        return post;
    }

    public Post postWithImage(User author, String imageUrl) {
        Post post = post(author);
        postImageMapper.insert(post.getId(), imageUrl, 0);
        return post;
    }

    public Comment comment(Post post, User author) {
        return comment(post, author, null);
    }

    /** {@code parent}にnull以外を渡すと返信(ネストしたコメント)になる。 */
    public Comment comment(Post post, User author, Comment parent) {
        Comment comment = new Comment();
        comment.setPostId(post.getId());
        comment.setUserId(author.getId());
        comment.setParentCommentId(parent == null ? null : parent.getId());
        comment.setBody("テストコメント");
        commentMapper.insert(comment);
        return comment;
    }
}
