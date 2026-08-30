package com.snsapp.backend.service.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.snsapp.backend.entity.RefreshToken;
import com.snsapp.backend.exception.InvalidRefreshTokenException;
import com.snsapp.backend.mapper.RefreshTokenMapper;
import com.snsapp.backend.security.JwtProperties;
import com.snsapp.backend.service.RefreshTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RefreshTokenService} の分岐網羅(docs/test-plan.md R-1〜R-6)。
 *
 * <p>実DBを使う {@code RefreshTokenServiceTest}(L3)と役割を分ける。ここでは
 * 「盗用検知時に本当に全トークンを失効させているか」「DBへ渡るのが生トークンではなく
 * ハッシュか」といった、Mapperへの引数を直接覗かないと確認できないことを検証する。
 *
 * <p>期限切れの検証は実時間を待たずに、期限が過去のトークンをモックで直接作って行う。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceUnitTest {

    private static final Long USER_ID = 7L;

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    // JwtPropertiesは値の入れ物でしかなくモックにする意味がないため、実物を組み立てて渡す。
    private JwtProperties jwtProperties;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setRefreshTokenExpirationSeconds(604800);
        refreshTokenService = new RefreshTokenService(refreshTokenMapper, jwtProperties);
    }

    /** Service内部と同じSHA-256のHex表現。DBへ渡る値がハッシュであることの検証に使う。 */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static RefreshToken token(Long id, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(id);
        refreshToken.setUserId(USER_ID);
        refreshToken.setTokenHash("dummy-hash");
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setRevokedAt(revokedAt);
        return refreshToken;
    }

    private static RefreshToken validToken() {
        return token(100L, LocalDateTime.now().plusDays(7), null);
    }

    /**
     * ローテーションで発行される後継トークン。
     *
     * <p>rotate は発行した直後に、その後継をハッシュで引き直してIDを得る。
     * 2度目の findByTokenHash が返す想定の値である。
     */
    private static RefreshToken successorToken() {
        return token(101L, LocalDateTime.now().plusDays(7), null);
    }

    // --- R-5: issue ---

    @Test
    void トークンを発行するとDBに保存される() {
        String rawToken = refreshTokenService.issue(USER_ID);

        assertThat(rawToken).isNotBlank();
        verify(refreshTokenMapper).insert(any(RefreshToken.class));
    }

    /**
     * DBに入るのはSHA-256ハッシュで、生トークンではないこと。
     * DBが漏れても、そこからトークンを復元して成りすませないようにするための設計。
     */
    @Test
    void DBには生トークンではなくハッシュが保存される() {
        String rawToken = refreshTokenService.issue(USER_ID);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(captor.getValue().getTokenHash()).isEqualTo(sha256Hex(rawToken));
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void 発行するトークンは毎回異なる() {
        assertThat(refreshTokenService.issue(USER_ID)).isNotEqualTo(refreshTokenService.issue(USER_ID));
    }

    @Test
    void 有効期限は設定値どおりに設定される() {
        jwtProperties.setRefreshTokenExpirationSeconds(3600);
        LocalDateTime before = LocalDateTime.now();

        refreshTokenService.issue(USER_ID);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getExpiresAt())
                .isAfterOrEqualTo(before.plusSeconds(3600))
                .isBefore(before.plusSeconds(3600).plusMinutes(1));
    }

    // --- R-1: 該当なし ---

    @Test
    void 存在しないトークンではローテーションできない() {
        when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(null);

        assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenMapper, never()).revoke(anyLong());
        verify(refreshTokenMapper, never()).insert(any());
    }

    /** 検索キーは生トークンではなくそのハッシュ。 */
    @Test
    void ローテーションの検索にはハッシュが使われる() {
        when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(null);

        assertThatThrownBy(() -> refreshTokenService.rotate("raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenMapper).findByTokenHash(sha256Hex("raw-token"));
    }

    // --- R-2: 盗用検知 ---

    /**
     * 一度使ったトークンが再提示された = 誰かがトークンを盗んだ可能性がある。
     * このとき攻撃者と正規利用者のどちらが提示したか区別できないため、
     * そのユーザーの全トークンを失効させて両方を強制的にログアウトさせる。
     */
    @Test
    void 失効済みトークンの再利用では全トークンが失効する() {
        when(refreshTokenMapper.findByTokenHash(anyString()))
                .thenReturn(token(100L, LocalDateTime.now().plusDays(7), LocalDateTime.now().minusMinutes(1)));

        assertThatThrownBy(() -> refreshTokenService.rotate("stolen-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenMapper).revokeAllForUser(USER_ID);
        verify(refreshTokenMapper, never()).insert(any());
    }

    // --- R-3: 期限切れ ---

    @Test
    void 期限切れのトークンではローテーションできない() {
        when(refreshTokenMapper.findByTokenHash(anyString()))
                .thenReturn(token(100L, LocalDateTime.now().minusSeconds(1), null));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenMapper, never()).revoke(anyLong());
        verify(refreshTokenMapper, never()).insert(any());
    }

    /** 期限切れは盗用ではないので、他のトークンまで巻き添えで失効させないこと。 */
    @Test
    void 期限切れでは他のトークンを失効させない() {
        when(refreshTokenMapper.findByTokenHash(anyString()))
                .thenReturn(token(100L, LocalDateTime.now().minusDays(1), null));

        assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenMapper, never()).revokeAllForUser(anyLong());
    }

    // --- R-4: 正常なローテーション ---

    @Test
    void 有効なトークンはローテーションできる() {
        when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(validToken());

        RefreshTokenService.RotationResult result = refreshTokenService.rotate("valid-token");

        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.newRawToken()).isNotBlank().isNotEqualTo("valid-token");
    }

    /**
     * 新トークンを発行してから、そのIDを記録して旧トークンを失効させる。
     *
     * <p><b>順序が逆になったのは、後継のIDを記録する必要が出たためである。</b>
     * 記録する相手がまだ存在しない状態では書けない。
     *
     * <p>順序が変わっても使い捨ての性質は失われない。
     * {@code rotate} は1つのトランザクションで実行され、途中で落ちれば
     * 両方とも巻き戻るためである。**トランザクションが無いと、
     * この順序は「2本とも有効」という状態を作り得る。**
     */
    @Test
    void ローテーションでは後継を発行してからそのIDを記録して旧を失効させる() {
        when(refreshTokenMapper.findByTokenHash(anyString()))
                .thenReturn(validToken(), successorToken());

        refreshTokenService.rotate("valid-token");

        InOrder inOrder = inOrder(refreshTokenMapper);
        inOrder.verify(refreshTokenMapper).insert(any(RefreshToken.class));
        inOrder.verify(refreshTokenMapper).revokeReplacedBy(100L, 101L);
    }

    // --- R-6: revoke ---

    @Test
    void トークンを失効できる() {
        when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(validToken());

        refreshTokenService.revoke("valid-token");

        verify(refreshTokenMapper).revoke(100L);
    }

    /** ログアウトは冪等にしたいので、該当トークンが無くても例外にしない。 */
    @Test
    void 存在しないトークンの失効は例外にならない() {
        when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(null);

        assertThatCode(() -> refreshTokenService.revoke("unknown-token")).doesNotThrowAnyException();

        verify(refreshTokenMapper, never()).revoke(anyLong());
    }
}
