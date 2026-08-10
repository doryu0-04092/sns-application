package com.snsapp.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.support.AbstractIntegrationTest;
import com.snsapp.backend.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * FollowMapper.xml のSQLの検証。
 *
 * <p>Serviceを経由せずMapperを直接呼ぶ。フォロー機能はService単体テスト(Mapperをモック)しか
 * 持っておらず、<strong>この4つのSQLは実DBに対して一度も実行されていなかった</strong>。
 * モックの向こう側にあるSQLは、Serviceのテストが何件通っても検証されない。
 *
 * <p>各テストは{@code @Transactional}でロールバックされるため、実行順序に依存しない。
 * 前のテストが作ったデータを前提にしないこと。
 */
@Transactional
class FollowMapperTest extends AbstractIntegrationTest {

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private TestFixtures fixtures;

    private static List<Long> userIdsOf(List<UserSummaryResponse> rows) {
        return rows.stream().map(UserSummaryResponse::userId).toList();
    }

    // --- insertIgnoreDuplicate / delete ---

    @Test
    void フォローするとフォロワー一覧に現れる() {
        User follower = fixtures.user("フォローする人");
        User followee = fixtures.user("される人");

        followMapper.insertIgnoreDuplicate(follower.getId(), followee.getId());

        assertThat(userIdsOf(followMapper.findFollowers(followee.getId(), follower.getId(), null, 20)))
                .containsExactly(follower.getId());
    }

    // ON CONFLICT DO NOTHING に守られている前提の挙動。Service側は重複チェックをしていないため、
    // ここが効かなくなると二重フォローで一意制約違反(500)になる。
    @Test
    void 二重フォローしても例外にならず1件のままになる() {
        User follower = fixtures.user();
        User followee = fixtures.user();

        followMapper.insertIgnoreDuplicate(follower.getId(), followee.getId());
        assertThatCode(() -> followMapper.insertIgnoreDuplicate(follower.getId(), followee.getId()))
                .doesNotThrowAnyException();

        assertThat(followMapper.findFollowers(followee.getId(), follower.getId(), null, 20)).hasSize(1);
    }

    @Test
    void アンフォローするとフォロワー一覧から消える() {
        User follower = fixtures.user();
        User followee = fixtures.user();
        followMapper.insertIgnoreDuplicate(follower.getId(), followee.getId());

        followMapper.delete(follower.getId(), followee.getId());

        assertThat(followMapper.findFollowers(followee.getId(), follower.getId(), null, 20)).isEmpty();
    }

    // Serviceは存在確認をせずDELETEを投げる(冪等な設計)。0件削除でエラーにならないことを固定する。
    @Test
    void フォローしていない相手のアンフォローは何も起きない() {
        User follower = fixtures.user();
        User followee = fixtures.user();

        assertThatCode(() -> followMapper.delete(follower.getId(), followee.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void アンフォローは他の人のフォロー関係に影響しない() {
        User followee = fixtures.user();
        User leaving = fixtures.user();
        User staying = fixtures.user();
        followMapper.insertIgnoreDuplicate(leaving.getId(), followee.getId());
        followMapper.insertIgnoreDuplicate(staying.getId(), followee.getId());

        followMapper.delete(leaving.getId(), followee.getId());

        assertThat(userIdsOf(followMapper.findFollowers(followee.getId(), followee.getId(), null, 20)))
                .containsExactly(staying.getId());
    }

    // --- findFollowers / findFollowing の向きの違い ---

    // followers と following は JOIN する列が逆なだけで見た目がほぼ同じSQL。
    // 取り違えても件数は合ってしまうことがあるため、両方向を1つのデータで確認する。
    @Test
    void フォロワー一覧とフォロー中一覧は向きが逆になる() {
        User me = fixtures.user("自分");
        User followsMe = fixtures.user("自分をフォローしている人");
        User iFollow = fixtures.user("自分がフォローしている人");
        followMapper.insertIgnoreDuplicate(followsMe.getId(), me.getId());
        followMapper.insertIgnoreDuplicate(me.getId(), iFollow.getId());

        assertThat(userIdsOf(followMapper.findFollowers(me.getId(), me.getId(), null, 20)))
                .containsExactly(followsMe.getId());
        assertThat(userIdsOf(followMapper.findFollowing(me.getId(), me.getId(), null, 20)))
                .containsExactly(iFollow.getId());
    }

    @Test
    void 一覧には表示名とアイコンキーが載る() {
        User me = fixtures.user("自分");
        User target = fixtures.user("山田太郎");
        followMapper.insertIgnoreDuplicate(me.getId(), target.getId());

        UserSummaryResponse row = followMapper.findFollowing(me.getId(), me.getId(), null, 20).get(0);

        assertThat(row.displayName()).isEqualTo("山田太郎");
        assertThat(row.userId()).isEqualTo(target.getId());
    }

    // --- isFollowing（相関サブクエリ） ---

    // 閲覧者から見た「自分がこの人をフォローしているか」。一覧に出す全員分をサブクエリで解決している。
    // ここが誤ると、フォロー一覧のボタンの状態が全員分おかしくなる。
    @Test
    void 相互フォローの相手はisFollowingがtrueになる() {
        User me = fixtures.user();
        User mutual = fixtures.user();
        followMapper.insertIgnoreDuplicate(mutual.getId(), me.getId());
        followMapper.insertIgnoreDuplicate(me.getId(), mutual.getId());

        UserSummaryResponse row = followMapper.findFollowers(me.getId(), me.getId(), null, 20).get(0);

        assertThat(row.isFollowing()).isTrue();
    }

    @Test
    void 片思いでフォローされているだけの相手はisFollowingがfalseになる() {
        User me = fixtures.user();
        User oneWay = fixtures.user();
        followMapper.insertIgnoreDuplicate(oneWay.getId(), me.getId());

        UserSummaryResponse row = followMapper.findFollowers(me.getId(), me.getId(), null, 20).get(0);

        assertThat(row.isFollowing()).isFalse();
    }

    // isFollowing は「閲覧者が誰か」で変わる。第三者から見た一覧では他人のフォロー状態が漏れてはいけない。
    @Test
    void isFollowingは閲覧者ごとに判定される() {
        User target = fixtures.user();
        User follower = fixtures.user();
        User viewer = fixtures.user();
        followMapper.insertIgnoreDuplicate(follower.getId(), target.getId());

        UserSummaryResponse seenByViewer =
                followMapper.findFollowers(target.getId(), viewer.getId(), null, 20).get(0);
        UserSummaryResponse seenBySelf =
                followMapper.findFollowers(target.getId(), follower.getId(), null, 20).get(0);

        // viewer は follower をフォローしていない
        assertThat(seenByViewer.isFollowing()).isFalse();
        // follower から見れば自分自身。自分自身はフォローできないので false のまま
        assertThat(seenBySelf.isFollowing()).isFalse();
    }

    // --- ページング（cursor / limit） ---

    @Test
    void limitで件数が絞られる() {
        User target = fixtures.user();
        for (int i = 0; i < 3; i++) {
            followMapper.insertIgnoreDuplicate(fixtures.user().getId(), target.getId());
        }

        assertThat(followMapper.findFollowers(target.getId(), target.getId(), null, 2)).hasSize(2);
    }

    // カーソルはユーザーIDではなくフォロー関係のレコードID(follows.id)。取り違えるとページが飛ぶ。
    @Test
    void カーソルを辿ると重複や欠落なく全件取得できる() {
        User target = fixtures.user();
        for (int i = 0; i < 5; i++) {
            followMapper.insertIgnoreDuplicate(fixtures.user().getId(), target.getId());
        }

        List<UserSummaryResponse> page1 = followMapper.findFollowers(target.getId(), target.getId(), null, 2);
        List<UserSummaryResponse> page2 =
                followMapper.findFollowers(target.getId(), target.getId(), lastId(page1), 2);
        List<UserSummaryResponse> page3 =
                followMapper.findFollowers(target.getId(), target.getId(), lastId(page2), 2);

        List<Long> all = concat(page1, page2, page3);
        assertThat(all).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void 新しいフォローほど先頭に並ぶ() {
        User target = fixtures.user();
        User first = fixtures.user();
        User second = fixtures.user();
        followMapper.insertIgnoreDuplicate(first.getId(), target.getId());
        followMapper.insertIgnoreDuplicate(second.getId(), target.getId());

        assertThat(userIdsOf(followMapper.findFollowers(target.getId(), target.getId(), null, 20)))
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    void 誰にもフォローされていなければ空になる() {
        User lonely = fixtures.user();

        assertThat(followMapper.findFollowers(lonely.getId(), lonely.getId(), null, 20)).isEmpty();
        assertThat(followMapper.findFollowing(lonely.getId(), lonely.getId(), null, 20)).isEmpty();
    }

    private static Long lastId(List<UserSummaryResponse> page) {
        return page.get(page.size() - 1).id();
    }

    @SafeVarargs
    private static List<Long> concat(List<UserSummaryResponse>... pages) {
        return java.util.Arrays.stream(pages).flatMap(List::stream).map(UserSummaryResponse::userId).toList();
    }
}
