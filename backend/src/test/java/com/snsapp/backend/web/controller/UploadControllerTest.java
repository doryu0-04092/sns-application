package com.snsapp.backend.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.snsapp.backend.controller.UploadController;
import com.snsapp.backend.exception.InvalidImageTypeException;
import com.snsapp.backend.storage.PresignedUpload;
import com.snsapp.backend.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@link UploadController} のWeb層スライステスト(docs/test-plan.md 4.2)。 */
@WebMvcTest(UploadController.class)
class UploadControllerTest extends AbstractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @Test
    void 署名付きURLを発行できる() throws Exception {
        when(storageService.createUploadUrl("image/png"))
                .thenReturn(new PresignedUpload("pending/abc.png", "https://s3.example.com/upload"));

        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentTypes\": [\"image/png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].key").value("pending/abc.png"))
                .andExpect(jsonPath("$.data[0].uploadUrl").value("https://s3.example.com/upload"));
    }

    /** レスポンスはリクエストの contentTypes と同じ順序で返る。 */
    @Test
    void 複数ファイル分をリクエストと同じ順序で返す() throws Exception {
        when(storageService.createUploadUrl("image/png"))
                .thenReturn(new PresignedUpload("pending/a.png", "https://s3.example.com/a"));
        when(storageService.createUploadUrl("image/jpeg"))
                .thenReturn(new PresignedUpload("pending/b.jpg", "https://s3.example.com/b"));

        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentTypes\": [\"image/png\", \"image/jpeg\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("pending/a.png"))
                .andExpect(jsonPath("$.data[1].key").value("pending/b.jpg"));
    }

    @Test
    void contentTypesが空配列なら400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentTypes\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(storageService, never()).createUploadUrl(anyString());
    }

    @Test
    void contentTypesが未指定なら400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void contentTypesが4件までは発行できる() throws Exception {
        when(storageService.createUploadUrl(anyString()))
                .thenReturn(new PresignedUpload("pending/a.png", "https://s3.example.com/a"));

        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentTypes\": [\"image/png\",\"image/png\",\"image/png\",\"image/png\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void contentTypesが5件なら400になる() throws Exception {
        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"contentTypes\": "
                                        + "[\"image/png\",\"image/png\",\"image/png\",\"image/png\",\"image/png\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(storageService, never()).createUploadUrl(anyString());
    }

    @Test
    void 許可されない形式なら400になる() throws Exception {
        when(storageService.createUploadUrl("application/pdf")).thenThrow(new InvalidImageTypeException());

        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentTypes\": [\"application/pdf\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_IMAGE_TYPE"));
    }

    /**
     * {@code @NotEmpty}/{@code @Size} はリスト自体しか検証しないため、要素のnullは素通りする。
     * 以前はここで {@code Map.of().get(null)} がNPEを投げて500になっていた
     * (docs/test-plan.md 6.1 不具合1)。他の不正な形式と同じ400になることを固定する。
     */
    @Test
    void contentTypesの要素がnullでも500ではなく400になる() throws Exception {
        when(storageService.createUploadUrl(any())).thenThrow(new InvalidImageTypeException());

        mockMvc.perform(authenticated(post("/api/uploads/presign"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentTypes\": [null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_IMAGE_TYPE"));
    }
}
