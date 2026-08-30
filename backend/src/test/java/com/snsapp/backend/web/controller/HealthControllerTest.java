package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.HealthController;
import com.snsapp.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link HealthController} のWeb層スライステスト。
 *
 * <p><b>見たいのは「DBが落ちたときに何が起きるか」である。</b>
 *
 * <p>元は {@code /api/health} 1本がDB疎通まで見ており、それをロードバランサの
 * ヘルスチェックに向けていた。そのため<b>RDSが一時的に不調になると全タスクが
 * 同時にunhealthyと判定され、一斉に置き換えが走る</b>構造だった。
 * 置き換えてもRDSは回復しないので、動いているタスクを失うぶん状況が悪化するだけである。
 *
 * <p>分離が効いていることは、<b>DBを落とした状態で livez が200のままであること</b>を
 * 確かめて初めて言える。設定を読むだけでは分からない。
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserMapper userMapper;

    /** DBが落ちている状態を作る。 */
    private void databaseIsDown() {
        when(userMapper.countUsers())
                .thenThrow(new DataAccessResourceFailureException("接続できない"));
    }

    // ------------------------------------------------------------------
    // livez — 依存先を一切見ない
    // ------------------------------------------------------------------

    @Test
    void livezはDBを見ずに200を返す() throws Exception {
        mockMvc.perform(get("/api/livez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));

        // **DBに問い合わせていないこと。** 問い合わせていれば、
        // DBの不調がそのままタスクの置き換えにつながる。
        verify(userMapper, never()).countUsers();
    }

    @Test
    void livezはDBが落ちていても200を返す() throws Exception {
        databaseIsDown();

        // **ここがこの分離の目的である。**
        // 200のままなら、RDSの不調でタスクが置き換わることはない。
        mockMvc.perform(get("/api/livez"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    // ------------------------------------------------------------------
    // readyz — 依存先を含む
    // ------------------------------------------------------------------

    @Test
    void readyzはDBの疎通を含めて200を返す() throws Exception {
        when(userMapper.countUsers()).thenReturn(3L);

        mockMvc.perform(get("/api/readyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.userCount").value(3));
    }

    @Test
    void readyzはDBが落ちていれば200を返さない() throws Exception {
        databaseIsDown();

        // **こちらは落ちてよい。** デプロイ時の投入判定と状況確認に使うもので、
        // ロードバランサは見ていない。
        mockMvc.perform(get("/api/readyz"))
                .andExpect(status().is5xxServerError());
    }

    // ------------------------------------------------------------------
    // health — 後方互換
    // ------------------------------------------------------------------

    /**
     * 分離する前からある名前。監視やスクリプトから参照されている可能性があるため、
     * <b>黙って消さずに readyz と同じ内容で残している。</b>
     */
    @Test
    void healthはreadyzと同じ内容を返す() throws Exception {
        when(userMapper.countUsers()).thenReturn(3L);

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.userCount").value(3));
    }

    @Test
    void healthはDBが落ちていれば200を返さない() throws Exception {
        databaseIsDown();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().is5xxServerError());
    }
}
