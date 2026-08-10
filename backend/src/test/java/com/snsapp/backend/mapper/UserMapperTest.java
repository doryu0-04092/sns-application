package com.snsapp.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserMapper.xml のSQLの検証。
 *
 * <p>特に {@code findProfileById} は「フォロワー数・フォロー中数・自分自身か・フォロー済みか」を
 * 1本のSQLの中で相関サブクエリとして解決している。<strong>実DBで実行するまで正しさが分からない</strong>
 * 種類のSQLだが、これまで実DBに対して一度も実行されていなかった。
 *
 * <p>{@code searchByDisplayName} の {@code ILIKE} はPostgreSQL固有の構文で、
 * 大文字小文字を無視した部分一致になる。インメモリDBでは同じ動きにならないため実DBで確認する。
 */
@Transactional
class UserMapperTest extends AbstractIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private TestFixtures fixtures;

    private static List<Long> userIdsOf(List<UserSummaryResponse> rows) {
        return rows.stream().map(UserSummaryResponse::userId).toList();
    }

    // --- findProfileById（相関サブクエリ） ---

    @Test
    void プロフィールの表示項目が取得できる() {
        User user = fixtures.user("山田太郎");

        ProfileResponse profile = userMapper.findProfileById(user.getId(), user.getId());

        assertThat(profile.id()).isEqualTo(user.getId());
        assertThat(profile.displayName()).isEqualTo("山田太郎");
    }

    @Test
    void 存在しないユーザーはnullになる() {
        assertThat(userMapper.findProfileById(-1L, 1L)).isNull();
    }

    // followerCount と followingCount は向きが逆なだけの similar なサブクエリ。
    // 取り違えると「フォロワー0人なのに100人と表示される」ような壊れ方をする。
    @Test
    void フォロワー数とフォロー中数が別々に数えられる() {
        User target = fixtures.user();
        followMapper.insertIgnoreDuplicate(fixtures.user().getId(), target.getId());
        followMapper.insertIgnoreDuplicate(fixtures.user().getId(), target.getId());
        followMapper.insertIgnoreDuplicate(target.getId(), fixtures.user().getId());

        ProfileResponse profile = userMapper.findProfileById(target.getId(), target.getId());

        assertThat(profile.followerCount()).isEqualTo(2);
        assertThat(profile.followingCount()).isEqualTo(1);
    }

    @Test
    void フォロー関係が無ければ両方0になる() {
        User lonely = fixtures.user();

        ProfileResponse profile = userMapper.findProfileById(lonely.getId(), lonely.getId());

        assertThat(profile.followerCount()).isZero();
        assertThat(profile.followingCount()).isZero();
    }

    // isMine が誤ると、他人のプロフィールに編集導線が出てしまう。
    @Test
    void 自分のプロフィールはisMineがtrueになる() {
        User me = fixtures.user();

        assertThat(userMapper.findProfileById(me.getId(), me.getId()).isMine()).isTrue();
    }

    @Test
    void 他人のプロフィールはisMineがfalseになる() {
        User me = fixtures.user();
        User other = fixtures.user();

        assertThat(userMapper.findProfileById(other.getId(), me.getId()).isMine()).isFalse();
    }

    @Test
    void フォロー済みならisFollowingがtrueになる() {
        User me = fixtures.user();
        User target = fixtures.user();
        followMapper.insertIgnoreDuplicate(me.getId(), target.getId());

        assertThat(userMapper.findProfileById(target.getId(), me.getId()).isFollowing()).isTrue();
    }

    // 向きを取り違えると「相手にフォローされている」だけでフォロー済み表示になる。
    @Test
    void フォローされているだけではisFollowingはfalseになる() {
        User me = fixtures.user();
        User target = fixtures.user();
        followMapper.insertIgnoreDuplicate(target.getId(), me.getId());

        assertThat(userMapper.findProfileById(target.getId(), me.getId()).isFollowing()).isFalse();
    }

    // --- searchByDisplayName（ILIKE） ---

    @Test
    void 表示名の部分一致で検索できる() {
        User me = fixtures.user("検索する人");
        User target = fixtures.user("山田太郎");
        fixtures.user("鈴木花子");

        List<UserSummaryResponse> results =
                userMapper.searchByDisplayName(me.getId(), "山田", null, 50);

        assertThat(userIdsOf(results)).contains(target.getId());
    }

    // ILIKE はPostgreSQL固有。ここが LIKE に変わると大文字小文字の区別が復活し、検索が急に当たらなくなる。
    @Test
    void 検索は大文字小文字を区別しない() {
        User me = fixtures.user("検索する人");
        User target = fixtures.user("Yamada");

        assertThat(userIdsOf(userMapper.searchByDisplayName(me.getId(), "yamada", null, 50)))
                .contains(target.getId());
        assertThat(userIdsOf(userMapper.searchByDisplayName(me.getId(), "YAMADA", null, 50)))
                .contains(target.getId());
    }

    // 自分をフォローすることはできないため、候補に出しても操作できず邪魔になる。
    @Test
    void 検索結果に自分は含まれない() {
        User me = fixtures.user("自分だけの名前ABCXYZ");

        assertThat(userIdsOf(userMapper.searchByDisplayName(me.getId(), "自分だけの名前ABCXYZ", null, 50)))
                .doesNotContain(me.getId());
    }

    @Test
    void 検索語が無ければ絞り込まずに返す() {
        User me = fixtures.user();
        User other = fixtures.user();

        assertThat(userIdsOf(userMapper.searchByDisplayName(me.getId(), null, null, 50)))
                .contains(other.getId());
    }

    @Test
    void 一致しなければ空になる() {
        User me = fixtures.user();

        assertThat(userMapper.searchByDisplayName(me.getId(), "該当しない文字列ZZZQQQ", null, 50)).isEmpty();
    }

    @Test
    void 検索結果にもフォロー状態が載る() {
        User me = fixtures.user("検索する人");
        User target = fixtures.user("フォロー済みの人ABC");
        followMapper.insertIgnoreDuplicate(me.getId(), target.getId());

        UserSummaryResponse row = userMapper.searchByDisplayName(me.getId(), "フォロー済みの人ABC", null, 50).get(0);

        assertThat(row.isFollowing()).isTrue();
    }

    // --- findByEmail / findById / insert / update ---

    @Test
    void メールアドレスでユーザーを引ける() {
        User user = fixtures.user();

        assertThat(userMapper.findByEmail(user.getEmail()).getId()).isEqualTo(user.getId());
    }

    @Test
    void 登録されていないメールアドレスはnullになる() {
        assertThat(userMapper.findByEmail("not-registered-" + System.nanoTime() + "@example.com")).isNull();
    }

    // 採番はDB側(GENERATED ALWAYS AS IDENTITY)。insert後にIDが埋め戻らないと、
    // 直後の画像登録やレスポンス生成が全て壊れる。
    @Test
    void 登録するとIDが埋め戻される() {
        User user = new User();
        user.setEmail("insert-test-%d@example.com".formatted(System.nanoTime()));
        user.setPasswordHash("dummy-hash");
        user.setDisplayName("新規ユーザー");

        userMapper.insert(user);

        assertThat(user.getId()).isNotNull();
        assertThat(userMapper.findById(user.getId()).getDisplayName()).isEqualTo("新規ユーザー");
    }

    @Test
    void プロフィールを更新できる() {
        User user = fixtures.user("更新前");

        userMapper.update(user.getId(), "更新後", "自己紹介", "avatars/new.jpg");

        User updated = userMapper.findById(user.getId());
        assertThat(updated.getDisplayName()).isEqualTo("更新後");
        assertThat(updated.getBio()).isEqualTo("自己紹介");
        assertThat(updated.getAvatarKey()).isEqualTo("avatars/new.jpg");
    }

    // 更新は指定した1件だけに効くこと。WHERE句が抜けると全ユーザーの表示名が書き換わる。
    @Test
    void 更新は他のユーザーに影響しない() {
        User target = fixtures.user("対象");
        User other = fixtures.user("巻き込まれない人");

        userMapper.update(target.getId(), "更新後", null, null);

        assertThat(userMapper.findById(other.getId()).getDisplayName()).isEqualTo("巻き込まれない人");
    }

    @Test
    void 利用者数を数えられる() {
        long before = userMapper.countUsers();

        fixtures.user();

        assertThat(userMapper.countUsers()).isEqualTo(before + 1);
    }
}
