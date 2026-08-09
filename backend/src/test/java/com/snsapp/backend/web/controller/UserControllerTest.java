package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.UserController;
import com.snsapp.backend.dto.CursorPage;
import com.snsapp.backend.dto.ProfileResponse;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.dto.UserSummaryResponse;
import com.snsapp.backend.exception.InvalidImageTypeException;
import com.snsapp.backend.exception.UserNotFoundException;
import com.snsapp.backend.service.FollowService;
import com.snsapp.backend.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link UserController} のWeb層スライステスト(docs/test-plan.md 4.2)。
 *
 * <p>プロフィール編集は常に自分が対象で、パスにユーザーIDを取らない設計。
 * 他人を指定する余地が構造的に無いことを、ルーティングの観点で確かめる。
 */
@WebMvcTest(UserController.class)
class UserControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private FollowService followService;

    private static final ProfileResponse PROFILE =
            new ProfileResponse(7L, "山田", "自己紹介", null, 3, 5, false, true);

    private static final UserResponse ME = new UserResponse(1L, "me@example.com", "自分", null, null);

    private static CursorPage<UserSummaryResponse> page() {
        return new CursorPage<>(List.of(new UserSummaryResponse(58L, 7L, "山田", null, false)), "58");
    }

    private static String profileBody(String displayName, String bio) {
        return "{\"displayName\": \"%s\", \"bio\": \"%s\"}".formatted(displayName, bio);
    }

    // --- GET /api/users ---

    @Test
    void ユーザーを検索できる() throws Exception {
        when(userService.searchUsers(eq(USER_ID), eq("山田"), isNull(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users?query=山田")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value("58"));
    }

    /** query省略時はnullがそのままServiceへ渡り、limitの既定値は20。 */
    @Test
    void queryを省略しても一覧を取得できる() throws Exception {
        when(userService.searchUsers(any(), any(), any(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users"))).andExpect(status().isOk());

        verify(userService).searchUsers(USER_ID, null, null, 20);
    }

    /** 一覧の id はフォロー関係やユーザーのレコードIDで、userId とは別物。 */
    @Test
    void 一覧はidとuserIdを別々に返す() throws Exception {
        when(userService.searchUsers(any(), any(), any(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users")))
                .andExpect(jsonPath("$.data.items[0].id").value(58))
                .andExpect(jsonPath("$.data.items[0].userId").value(7));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 50, 51, 10000})
    void 範囲外のlimitでもエラーにならない(int limit) throws Exception {
        when(userService.searchUsers(any(), any(), any(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users?limit=" + limit))).andExpect(status().isOk());
    }

    @Test
    void cursorに文字列を渡すと400になる() throws Exception {
        mockMvc.perform(authenticated(get("/api/users?cursor=abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- GET /api/users/{userId} ---

    @Test
    void プロフィールを取得できる() throws Exception {
        when(userService.getProfile(USER_ID, 7L)).thenReturn(PROFILE);

        mockMvc.perform(authenticated(get("/api/users/7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.followerCount").value(3))
                .andExpect(jsonPath("$.data.followingCount").value(5));
    }

    @Test
    void 存在しないユーザーのプロフィールは404になる() throws Exception {
        when(userService.getProfile(any(), eq(999L))).thenThrow(new UserNotFoundException());

        mockMvc.perform(authenticated(get("/api/users/999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void ユーザーIDが数値でないと400になる() throws Exception {
        mockMvc.perform(authenticated(get("/api/users/abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    // --- PATCH /api/users/me ---

    @Test
    void 自分のプロフィールを編集できる() throws Exception {
        when(userService.updateProfile(eq(USER_ID), any())).thenReturn(ME);

        mockMvc.perform(authenticated(patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("新しい名前", "新しい自己紹介")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /**
     * 編集対象は常にログイン中のユーザー。パスにユーザーIDを取らないため、
     * 他人のIDを指定して編集する手段が構造的に存在しない。
     *
     * <p>{@code /api/users/{userId}} はGETのみ定義されているため、PATCHで来ると405になる。
     * この405は元々 {@code Exception} のcatch-allに落ちて500になっていた
     * (docs/test-plan.md 6.1 不具合2)。正しいステータスが返ることをここで固定する。
     */
    @Test
    void 他人のIDを指定して編集することはできない() throws Exception {
        mockMvc.perform(authenticated(patch("/api/users/7"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("乗っ取り", "")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));

        verify(userService, never()).updateProfile(any(), any());
    }

    /** 誤ったHTTPメソッドが500ではなく405で返ること(上と同じ修正の別ルート)。 */
    @Test
    void 定義されていないHTTPメソッドは405になる() throws Exception {
        mockMvc.perform(authenticated(patch("/api/users")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    /** PATCHだが displayName は必須。省略すると400。 */
    @Test
    void 表示名を省略すると400になる() throws Exception {
        mockMvc.perform(authenticated(patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"自己紹介だけ変えたい\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({"1, 200", "100, 200", "101, 400"})
    void 表示名の長さの境界値を検証する(int length, int expectedStatus) throws Exception {
        when(userService.updateProfile(any(), any())).thenReturn(ME);

        mockMvc.perform(authenticated(patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("あ".repeat(length), "")))
                .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @CsvSource({"0, 200", "499, 200", "500, 200", "501, 400"})
    void 自己紹介の長さの境界値を検証する(int length, int expectedStatus) throws Exception {
        when(userService.updateProfile(any(), any())).thenReturn(ME);

        mockMvc.perform(authenticated(patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileBody("名前", "あ".repeat(length))))
                .andExpect(status().is(expectedStatus));
    }

    /** アイコンの検証に落ちた場合、Service由来のエラーコードで返る。 */
    @Test
    void アイコンが不正なら400になる() throws Exception {
        when(userService.updateProfile(any(), any())).thenThrow(new InvalidImageTypeException());

        mockMvc.perform(authenticated(patch("/api/users/me"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"名前\", \"avatarKey\": \"pending/bad.exe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_IMAGE_TYPE"));
    }

    // --- GET /api/users/{userId}/followers, /following ---

    @Test
    void フォロワー一覧を取得できる() throws Exception {
        when(followService.listFollowers(eq(USER_ID), eq(7L), isNull(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users/7/followers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        verify(followService).listFollowers(USER_ID, 7L, null, 20);
    }

    @Test
    void フォロー中一覧を取得できる() throws Exception {
        when(followService.listFollowing(eq(USER_ID), eq(7L), isNull(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users/7/following")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        verify(followService).listFollowing(USER_ID, 7L, null, 20);
    }

    @Test
    void 存在しないユーザーのフォロワー一覧は404になる() throws Exception {
        when(followService.listFollowers(any(), eq(999L), any(), anyInt()))
                .thenThrow(new UserNotFoundException());

        mockMvc.perform(authenticated(get("/api/users/999/followers")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 存在しないユーザーのフォロー中一覧は404になる() throws Exception {
        when(followService.listFollowing(any(), eq(999L), any(), anyInt()))
                .thenThrow(new UserNotFoundException());

        mockMvc.perform(authenticated(get("/api/users/999/following")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void フォロワー一覧のcursorはそのままServiceへ渡る() throws Exception {
        when(followService.listFollowers(any(), any(), any(), anyInt())).thenReturn(page());

        mockMvc.perform(authenticated(get("/api/users/7/followers?cursor=58&limit=10")))
                .andExpect(status().isOk());

        verify(followService).listFollowers(USER_ID, 7L, 58L, 10);
    }
}
