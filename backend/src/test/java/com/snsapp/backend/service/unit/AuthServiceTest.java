package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.snsapp.backend.dto.LoginRequest;
import com.snsapp.backend.dto.SignupRequest;
import com.snsapp.backend.dto.UserResponse;
import com.snsapp.backend.entity.User;
import com.snsapp.backend.exception.DuplicateEmailException;
import com.snsapp.backend.exception.InvalidCredentialsException;
import com.snsapp.backend.exception.UnauthenticatedException;
import com.snsapp.backend.mapper.UserMapper;
import com.snsapp.backend.service.AuthService;
import com.snsapp.backend.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link com.snsapp.backend.service.AuthService} の分岐網羅(docs/test-plan.md A-1〜A-8)。
 *
 * <p>DBを使わずMapperをモックするため、「メールが重複している」「ユーザーが消えている」といった
 * 実DBでは作りにくい状態をそのまま作れる。SQLの正しさはL3の結合テストが担当する。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private AuthService authService;

    private static User user(Long id, String email, String displayName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash("hashed");
        user.setDisplayName(displayName);
        return user;
    }

    // --- A-1: signup 正常系 ---

    @Test
    void 未登録のメールアドレスならユーザーを登録できる() {
        when(userMapper.findByEmail("new@example.com")).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");

        UserResponse response =
                authService.signup(new SignupRequest("new@example.com", "password123", "新規ユーザー"));

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.displayName()).isEqualTo("新規ユーザー");
    }

    /** 平文のまま保存していないこと。ハッシュ化はPasswordEncoderに委譲されている。 */
    @Test
    void signupではパスワードをハッシュ化して保存する() {
        when(userMapper.findByEmail(anyString())).thenReturn(null);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");

        authService.signup(new SignupRequest("new@example.com", "password123", "新規ユーザー"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password123");
    }

    // --- A-2: signup 異常系 ---

    @Test
    void 登録済みのメールアドレスでは登録できない() {
        when(userMapper.findByEmail("taken@example.com")).thenReturn(user(1L, "taken@example.com", "既存"));

        assertThatThrownBy(() -> authService.signup(
                        new SignupRequest("taken@example.com", "password123", "新規ユーザー")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    /** 重複を検出した時点で打ち切り、ハッシュ計算もinsertも行わないこと。 */
    @Test
    void メールアドレス重複時はユーザーを保存しない() {
        when(userMapper.findByEmail(anyString())).thenReturn(user(1L, "taken@example.com", "既存"));

        assertThatThrownBy(() -> authService.signup(
                        new SignupRequest("taken@example.com", "password123", "新規ユーザー")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userMapper, never()).insert(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // --- A-3〜A-5: login ---

    @Test
    void 正しいメールアドレスとパスワードでログインできる() {
        when(userMapper.findByEmail("user@example.com")).thenReturn(user(7L, "user@example.com", "山田"));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        UserResponse response = authService.login(new LoginRequest("user@example.com", "password123"));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.displayName()).isEqualTo("山田");
    }

    @Test
    void 存在しないメールアドレスではログインできない() {
        when(userMapper.findByEmail("unknown@example.com")).thenReturn(null);

        assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void パスワードが違うとログインできない() {
        when(userMapper.findByEmail("user@example.com")).thenReturn(user(7L, "user@example.com", "山田"));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    /**
     * 「ユーザーが存在しない」と「パスワードが違う」を同じ例外にしている設計を固定する。
     * 区別するとレスポンスの差からメールアドレスの登録有無を判定できてしまう(ユーザー列挙攻撃)。
     */
    @Test
    void ユーザー不在とパスワード不一致は同じ例外で区別できない() {
        when(userMapper.findByEmail("unknown@example.com")).thenReturn(null);
        when(userMapper.findByEmail("user@example.com")).thenReturn(user(7L, "user@example.com", "山田"));
        when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

        Throwable byMissingUser =
                catchThrowable(() -> authService.login(new LoginRequest("unknown@example.com", "password123")));
        Throwable byWrongPassword =
                catchThrowable(() -> authService.login(new LoginRequest("user@example.com", "wrong-password")));

        assertThat(byMissingUser).isExactlyInstanceOf(InvalidCredentialsException.class);
        assertThat(byWrongPassword).isExactlyInstanceOf(InvalidCredentialsException.class);
        assertThat(byMissingUser.getMessage()).isEqualTo(byWrongPassword.getMessage());
    }

    // --- A-6〜A-8: getCurrentUser ---

    @Test
    void 認証済みユーザーの情報を取得できる() {
        when(userMapper.findById(7L)).thenReturn(user(7L, "user@example.com", "山田"));

        assertThat(authService.getCurrentUser(7L).id()).isEqualTo(7L);
    }

    /** トークンは有効だが該当ユーザーがDBに存在しない場合。401にして再ログインを促す。 */
    @Test
    void トークンが指すユーザーが存在しなければ認証エラーになる() {
        when(userMapper.findById(999L)).thenReturn(null);

        assertThatThrownBy(() -> authService.getCurrentUser(999L))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void アイコン未設定ならavatarUrlはnullになる() {
        when(userMapper.findById(7L)).thenReturn(user(7L, "user@example.com", "山田"));
        when(storageService.presignedGetUrl(null)).thenReturn(null);

        assertThat(authService.getCurrentUser(7L).avatarUrl()).isNull();
    }

    @Test
    void アイコン設定済みなら署名付きURLがavatarUrlに入る() {
        User withAvatar = user(7L, "user@example.com", "山田");
        withAvatar.setAvatarKey("avatars/abc.jpg");
        when(userMapper.findById(7L)).thenReturn(withAvatar);
        when(storageService.presignedGetUrl("avatars/abc.jpg")).thenReturn("https://s3.example.com/signed");

        assertThat(authService.getCurrentUser(7L).avatarUrl()).isEqualTo("https://s3.example.com/signed");
    }
}
