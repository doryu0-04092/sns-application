package com.snsapp.backend.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * StorageServiceが保存したファイルを/uploads/**として静的配信する。
 * JwtAuthFilterは/api/配下のみ検証するため、/uploads/**は認証不要でそのまま配信される
 * (imgタグから直接参照できるようにするため意図的)。
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Path.of(uploadDir).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
