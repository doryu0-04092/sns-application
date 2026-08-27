package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UpdateProfileRequest;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.InvalidImageTypeException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.service.UserService;
import com.snsapp.backend.storage.StorageService;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserService} の分岐網羅(docs/test-plan.md U-1〜U-12)。
 *
 * <p>アイコン差し替え時の「promote -> 旧アイコン削除 -> DB更新」という副作用の順序と、
 * 検索のlimitクランプ・カーソル算出をここで固定する。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserService userService;

    private static User user(Long id, String avatarKey) {
        User user = new User();
        user.setId(id);
        user.setEmail("user%d@example.com".formatted(id));
        user.setDisplayName("ユーザー%d".formatted(id));
        user.setAvatarKey(avatarKey);
        return user;
    }

    private static ProfileResponse profile(Long id, String avatarKey) {
        return new ProfileResponse(id, "山田", "自己紹介", avatarKey, 3, 5, false, true);
    }

    private static UserSummaryResponse summary(long id) {
        return new UserSummaryResponse(id, id, "ユーザー" + id, "avatars/%d.jpg".formatted(id), false);
    }

    private static List<UserSummaryResponse> summaries(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> summary(i)).toList();
    }

    // --- U-1, U-2: getProfile ---

    @Test
    void 存在するユーザーのプロフィールを取得できる() {
        when(userMapper.findProfileById(7L, 1L)).thenReturn(profile(7L, "avatars/abc.jpg"));
        when(storageService.viewUrl("avatars/abc.jpg")).thenReturn("https://s3.example.com/signed");

        ProfileResponse result = userService.getProfile(1L, 7L);

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.followerCount()).isEqualTo(3);
    }

    /** MyBatisが入れるのはS3キー。返す直前に表示可能な署名付きURLへ差し替わること。 */
    @Test
    void プロフィールのアイコンはS3キーではなく署名付きURLで返る() {
        when(userMapper.findProfileById(7L, 1L)).thenReturn(profile(7L, "avatars/abc.jpg"));
        when(storageService.viewUrl("avatars/abc.jpg")).thenReturn("https://s3.example.com/signed");

        assertThat(userService.getProfile(1L, 7L).avatarUrl()).isEqualTo("https://s3.example.com/signed");
    }

    @Test
    void 存在しないユーザーのプロフィールは取得できない() {
        when(userMapper.findProfileById(999L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> userService.getProfile(1L, 999L)).isInstanceOf(UserNotFoundException.class);
    }

    // --- U-3, U-4: updateProfile アイコンを変えない場合 ---

    @Test
    void アイコンキーがnullなら既存のアイコンを維持する() {
        when(userMapper.findById(1L)).thenReturn(user(1L, "avatars/old.jpg"));

        userService.updateProfile(1L, new UpdateProfileRequest("新しい名前", "新しい自己紹介", null));

        verify(userMapper).update(1L, "新しい名前", "新しい自己紹介", "avatars/old.jpg");
        verify(storageService, never()).promote(anyString(), anyString());
        verify(storageService, never()).delete(anyString());
    }

    /** 空文字・空白のみは「未指定」と同じ扱い。isBlank()の分岐。 */
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void アイコンキーが空白のみなら既存のアイコンを維持する(String avatarKey) {
        when(userMapper.findById(1L)).thenReturn(user(1L, "avatars/old.jpg"));

        userService.updateProfile(1L, new UpdateProfileRequest("新しい名前", null, avatarKey));

        verify(userMapper).update(1L, "新しい名前", null, "avatars/old.jpg");
        verify(storageService, never()).promote(anyString(), anyString());
    }

    // --- U-5, U-6: updateProfile アイコンを差し替える場合 ---

    /**
     * 新しいアイコンをpromoteしてから旧アイコンを消し、最後にDBを更新する。
     * 順序が入れ替わると、promoteに失敗した時に旧アイコンだけ消えて表示できなくなる。
     */
    @Test
    void 新しいアイコンを指定するとpromoteと旧アイコン削除を経てDBが更新される() {
        when(userMapper.findById(1L)).thenReturn(user(1L, "avatars/old.jpg"));
        when(storageService.promote("pending/new.jpg", "avatars")).thenReturn("avatars/new.jpg");

        userService.updateProfile(1L, new UpdateProfileRequest("名前", "自己紹介", "pending/new.jpg"));

        InOrder inOrder = inOrder(storageService, userMapper);
        inOrder.verify(storageService).promote("pending/new.jpg", "avatars");
        inOrder.verify(storageService).delete("avatars/old.jpg");
        inOrder.verify(userMapper).update(1L, "名前", "自己紹介", "avatars/new.jpg");
    }

    /** アイコン未設定のユーザーが初めて設定する場合、削除対象のキーはnullになる。 */
    @Test
    void アイコン未設定のユーザーが初めて設定してもエラーにならない() {
        when(userMapper.findById(1L)).thenReturn(user(1L, null));
        when(storageService.promote("pending/new.jpg", "avatars")).thenReturn("avatars/new.jpg");

        userService.updateProfile(1L, new UpdateProfileRequest("名前", null, "pending/new.jpg"));

        verify(storageService).delete(null);
        verify(userMapper).update(1L, "名前", null, "avatars/new.jpg");
    }

    /** 画像の検証に落ちた場合、DBを更新しないこと。表示名だけ変わって画像が変わらない中途半端な状態を防ぐ。 */
    @Test
    void アイコンのpromoteに失敗したらDBを更新しない() {
        when(userMapper.findById(1L)).thenReturn(user(1L, "avatars/old.jpg"));
        when(storageService.promote("pending/bad.exe", "avatars")).thenThrow(new InvalidImageTypeException());

        assertThatThrownBy(() -> userService.updateProfile(
                        1L, new UpdateProfileRequest("名前", null, "pending/bad.exe")))
                .isInstanceOf(InvalidImageTypeException.class);

        verify(userMapper, never()).update(any(), any(), any(), any());
        verify(storageService, never()).delete(anyString());
    }

    // --- U-7〜U-9: searchUsers のクエリ正規化 ---

    @Test
    void 検索クエリがnullなら絞り込まずに全件を返す() {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), anyInt())).thenReturn(summaries(3));

        assertThat(userService.searchUsers(1L, null, null, 20).items()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 検索クエリが空白のみならnullとして扱う(String query) {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), anyInt())).thenReturn(List.of());

        userService.searchUsers(1L, query, null, 20);

        verify(userMapper).searchByDisplayName(eq(1L), isNull(), isNull(), anyInt());
    }

    @Test
    void 検索クエリの前後の空白は取り除かれる() {
        when(userMapper.searchByDisplayName(eq(1L), eq("山田"), isNull(), anyInt())).thenReturn(List.of());

        userService.searchUsers(1L, "  山田  ", null, 20);

        verify(userMapper).searchByDisplayName(eq(1L), eq("山田"), isNull(), anyInt());
    }

    // --- U-10: limit のクランプ ---

    /**
     * 範囲外のlimitは例外にせず1〜50へ丸める。Mapperへは「次ページの有無を判定するための1件」を
     * 足した clampedLimit + 1 が渡る。
     */
    @ParameterizedTest
    @CsvSource({"-5, 2", "0, 2", "1, 2", "20, 21", "50, 51", "51, 51", "1000, 51"})
    void 検索のlimitは1から50に丸められる(int requested, int expectedMapperLimit) {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), anyInt())).thenReturn(List.of());

        userService.searchUsers(1L, null, null, requested);

        verify(userMapper).searchByDisplayName(eq(1L), isNull(), isNull(), eq(expectedMapperLimit));
    }

    // --- U-11, U-12: ページング ---

    @Test
    void 次ページがある場合はnextCursorに最後の要素のidが入る() {
        // limit=3 に対して4件返る = 次ページあり
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), eq(4))).thenReturn(summaries(4));

        CursorPage<UserSummaryResponse> page = userService.searchUsers(1L, null, null, 3);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isEqualTo("3");
    }

    @Test
    void 次ページがない場合はnextCursorがnullになる() {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), eq(4))).thenReturn(summaries(2));

        CursorPage<UserSummaryResponse> page = userService.searchUsers(1L, null, null, 3);

        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 検索結果が0件でも例外にならない() {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), anyInt())).thenReturn(List.of());

        CursorPage<UserSummaryResponse> page = userService.searchUsers(1L, null, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 検索結果のアイコンも署名付きURLに差し替わる() {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), isNull(), anyInt())).thenReturn(summaries(1));
        when(storageService.viewUrl("avatars/1.jpg")).thenReturn("https://s3.example.com/signed");

        assertThat(userService.searchUsers(1L, null, null, 20).items().get(0).avatarUrl())
                .isEqualTo("https://s3.example.com/signed");
    }

    @Test
    void カーソルはそのままMapperへ渡される() {
        when(userMapper.searchByDisplayName(eq(1L), isNull(), eq(42L), anyInt())).thenReturn(List.of());

        userService.searchUsers(1L, null, 42L, 20);

        ArgumentCaptor<Long> cursor = ArgumentCaptor.forClass(Long.class);
        verify(userMapper).searchByDisplayName(eq(1L), isNull(), cursor.capture(), anyInt());
        assertThat(cursor.getValue()).isEqualTo(42L);
    }
}
